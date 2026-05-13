package ca.dnamobile.javalauncher.ui.version;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fetches mod-loader version lists from their official APIs.
 * Supported loaders: Fabric, NeoForge, Forge (legacy).
 */
public final class LoaderVersionResolver {

    private static final String FABRIC_LOADER_URL  =
            "https://meta.fabricmc.net/v2/versions/loader/%s";
    private static final String NEOFORGE_VERSIONS_URL =
            "https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge";
    private static final String FORGE_PROMO_URL =
            "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json";

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private LoaderVersionResolver() {}

    // ── LoaderVersionOption ───────────────────────────────────────────────────

    /**
     * A single loader version entry shown in the create-instance dialog spinner.
     *
     * {@code displayName} is the label shown to the user.
     * {@code versionId}   is the identifier passed to the installer.
     */
    public static final class LoaderVersionOption {
        @NonNull  public final String displayName;
        @NonNull  public final String versionId;

        public LoaderVersionOption(@NonNull String displayName, @NonNull String versionId) {
            this.displayName = displayName;
            this.versionId   = versionId;
        }

        @NonNull
        @Override
        public String toString() {
            return displayName;
        }
    }

    /**
     * Synchronously fetches loader version options.
     * Must be called from a background thread.
     *
     * @param loader          loader name ("fabric", "forge", "neoforge")
     * @param minecraftVersion Minecraft version string (e.g. "1.20.4")
     * @return list of options, may be empty but never null
     * @throws IOException on network failure
     */
    @NonNull
    public static ArrayList<LoaderVersionOption> resolveVersions(
            @Nullable String loader,
            @Nullable String minecraftVersion
    ) throws IOException {
        if (loader == null || minecraftVersion == null) return new ArrayList<>();

        switch (loader.toLowerCase(java.util.Locale.ROOT)) {
            case "fabric":
                return resolveFabricSync(minecraftVersion);
            case "neoforge":
                return resolveNeoForgeSync(minecraftVersion);
            case "forge":
                return resolveForgeSync(minecraftVersion);
            default:
                return new ArrayList<>();
        }
    }

    @NonNull
    private static ArrayList<LoaderVersionOption> resolveFabricSync(
            @NonNull String minecraftVersion
    ) throws IOException {
        String json = httpGet(String.format(java.util.Locale.ROOT, FABRIC_LOADER_URL, minecraftVersion));
        ArrayList<LoaderVersionOption> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                JSONObject loaderObj = obj.optJSONObject("loader");
                if (loaderObj == null) continue;
                String version = loaderObj.optString("version", "");
                boolean stable = loaderObj.optBoolean("stable", true);
                if (version.isEmpty()) continue;
                String label = stable ? version : version + " (beta)";
                result.add(new LoaderVersionOption(label, version));
            }
        } catch (Exception e) {
            throw new IOException("Fabric parse error: " + e.getMessage(), e);
        }
        return result;
    }

    @NonNull
    private static ArrayList<LoaderVersionOption> resolveNeoForgeSync(
            @NonNull String minecraftVersion
    ) throws IOException {
        String json = httpGet(NEOFORGE_VERSIONS_URL);
        ArrayList<LoaderVersionOption> result = new ArrayList<>();
        String prefix = buildNeoForgePrefix(minecraftVersion);
        try {
            JSONObject root = new JSONObject(json);
            JSONArray versions = root.optJSONArray("versions");
            if (versions == null) return result;
            for (int i = versions.length() - 1; i >= 0; i--) {
                String v = versions.getString(i);
                if (prefix == null || v.startsWith(prefix)) {
                    result.add(new LoaderVersionOption(v, v));
                }
            }
        } catch (Exception e) {
            throw new IOException("NeoForge parse error: " + e.getMessage(), e);
        }
        return result;
    }

    @Nullable
    private static String buildNeoForgePrefix(@NonNull String mcVersion) {
        String[] parts = mcVersion.split("\\.");
        if (parts.length < 2) return null;
        try {
            int minor = Integer.parseInt(parts[1]);
            String patch = parts.length >= 3 ? parts[2] : "";
            return minor + (patch.isEmpty() ? "." : "." + patch + ".");
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @NonNull
    private static ArrayList<LoaderVersionOption> resolveForgeSync(
            @NonNull String minecraftVersion
    ) throws IOException {
        String json = httpGet(FORGE_PROMO_URL);
        ArrayList<LoaderVersionOption> result = new ArrayList<>();
        try {
            JSONObject promos = new JSONObject(json).getJSONObject("promos");
            String recommended = promos.optString(minecraftVersion + "-recommended", null);
            String latest      = promos.optString(minecraftVersion + "-latest",      null);
            if (recommended != null) {
                String full = minecraftVersion + "-" + recommended;
                result.add(new LoaderVersionOption(full + " (recommended)", full));
            }
            if (latest != null && !latest.equals(recommended)) {
                String full = minecraftVersion + "-" + latest;
                result.add(new LoaderVersionOption(full + " (latest)", full));
            }
        } catch (Exception e) {
            throw new IOException("Forge parse error: " + e.getMessage(), e);
        }
        return result;
    }

    // ── Async helpers (kept for other callers) ────────────────────────────────

    public interface FabricCallback {
        void onResult(@NonNull List<String> loaderVersions);
        void onError(@NonNull String message);
    }

    public static void fetchFabricLoaderVersions(
            @NonNull String minecraftVersion,
            @NonNull FabricCallback callback
    ) {
        EXECUTOR.execute(() -> {
            try {
                String json = httpGet(String.format(FABRIC_LOADER_URL, minecraftVersion));
                JSONArray arr = new JSONArray(json);
                List<String> versions = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject entry = arr.getJSONObject(i);
                    JSONObject loaderObj = entry.getJSONObject("loader");
                    versions.add(loaderObj.getString("version"));
                }
                MAIN.post(() -> callback.onResult(versions));
            } catch (Throwable t) {
                String msg = t.getMessage() != null ? t.getMessage() : t.toString();
                MAIN.post(() -> callback.onError(msg));
            }
        });
    }

    public interface NeoForgeCallback {
        void onResult(@NonNull List<String> versions);
        void onError(@NonNull String message);
    }

    public static void fetchNeoForgeVersions(
            @NonNull String minecraftVersion,
            @NonNull NeoForgeCallback callback
    ) {
        EXECUTOR.execute(() -> {
            try {
                String json = httpGet(NEOFORGE_VERSIONS_URL);
                JSONObject obj = new JSONObject(json);
                JSONArray arr = obj.optJSONArray("versions");
                List<String> matching = new ArrayList<>();
                if (arr != null) {
                    String prefix2 = minecraftVersion.length() > 2
                            ? minecraftVersion.substring(2)
                            : minecraftVersion;
                    for (int i = arr.length() - 1; i >= 0; i--) {
                        String v = arr.getString(i);
                        if (v.startsWith(prefix2 + ".") || v.startsWith(prefix2 + "-")) {
                            matching.add(v);
                        }
                    }
                }
                MAIN.post(() -> callback.onResult(matching));
            } catch (Throwable t) {
                String msg = t.getMessage() != null ? t.getMessage() : t.toString();
                MAIN.post(() -> callback.onError(msg));
            }
        });
    }

    public interface ForgeCallback {
        void onResult(@NonNull List<String> versions);
        void onError(@NonNull String message);
    }

    public static void fetchForgeVersions(
            @NonNull String minecraftVersion,
            @NonNull ForgeCallback callback
    ) {
        EXECUTOR.execute(() -> {
            try {
                String json = httpGet(FORGE_PROMO_URL);
                JSONObject promos = new JSONObject(json).getJSONObject("promos");
                List<String> versions = new ArrayList<>();
                java.util.Iterator<String> keys = promos.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    if (key.startsWith(minecraftVersion + "-")) {
                        versions.add(minecraftVersion + "-" + promos.getString(key));
                    }
                }
                MAIN.post(() -> callback.onResult(versions));
            } catch (Throwable t) {
                String msg = t.getMessage() != null ? t.getMessage() : t.toString();
                MAIN.post(() -> callback.onError(msg));
            }
        });
    }

    // ── HTTP helper ───────────────────────────────────────────────────────────

    @NonNull
    private static String httpGet(@NonNull String requestUrl) throws IOException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(requestUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(12_000);
            conn.setReadTimeout(12_000);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "DroidBridgeLauncher/1.0");

            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("HTTP " + code + " from " + requestUrl);
            }

            try (BufferedInputStream in = new BufferedInputStream(conn.getInputStream());
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int read;
                while ((read = in.read(buf)) != -1) out.write(buf, 0, read);
                return out.toString(StandardCharsets.UTF_8.name());
            }
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}

