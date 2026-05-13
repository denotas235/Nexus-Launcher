package ca.dnamobile.javalauncher.ui.version;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
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

    // ── Fabric ────────────────────────────────────────────────────────────────

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
                    JSONObject loader = entry.getJSONObject("loader");
                    versions.add(loader.getString("version"));
                }
                MAIN.post(() -> callback.onResult(versions));
            } catch (Throwable t) {
                String msg = t.getMessage() != null ? t.getMessage() : t.toString();
                MAIN.post(() -> callback.onError(msg));
            }
        });
    }

    // ── NeoForge ──────────────────────────────────────────────────────────────

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
                    // NeoForge versions follow pattern: MC_MAJOR.MC_MINOR.MC_PATCH-NEOFORGE
                    // e.g. "21.1.0" for MC 1.21.1
                    String prefix = minecraftVersion.length() > 2
                            ? minecraftVersion.substring(2) // strip "1."
                            : minecraftVersion;
                    for (int i = arr.length() - 1; i >= 0; i--) {
                        String v = arr.getString(i);
                        if (v.startsWith(prefix + ".") || v.startsWith(prefix + "-")) {
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

    // ── Forge (legacy) ────────────────────────────────────────────────────────

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
    private static String httpGet(@NonNull String requestUrl) throws Exception {
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
                throw new IllegalStateException("HTTP " + code + " from " + requestUrl);
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
