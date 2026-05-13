package ca.dnamobile.javalauncher.launcher;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

/**
 * Resolves loader version options (Fabric, Forge, NeoForge, etc.)
 * for a given Minecraft version by querying the relevant metadata API.
 */
public final class LoaderVersionResolver {

    private static final String FABRIC_META_URL =
            "https://meta.fabricmc.net/v2/versions/loader/%s";
    private static final String NEOFORGE_META_URL =
            "https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge";

    private LoaderVersionResolver() {}

    // ── LoaderVersionOption ───────────────────────────────────────────────────

    /** Represents a single loader version option for display in the UI. */
    public static final class LoaderVersionOption {
        /** The human-readable display name shown in the spinner. */
        @NonNull
        public final String displayName;

        /** The version identifier used internally / passed to the installer. */
        @NonNull
        public final String versionId;

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

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Fetches loader version options for the given loader and Minecraft version.
     *
     * <p>This method performs a network request and must be called from a background thread.
     *
     * @param loader           loader identifier (e.g. "fabric", "forge", "neoforge")
     * @param minecraftVersion the Minecraft version string (e.g. "1.20.4")
     * @return list of version options, empty if none are found or if the loader is unknown
     * @throws IOException if the network request fails
     */
    @NonNull
    public static ArrayList<LoaderVersionOption> resolveVersions(
            @Nullable String loader,
            @Nullable String minecraftVersion
    ) throws IOException {
        if (loader == null || minecraftVersion == null) return new ArrayList<>();

        switch (loader.toLowerCase(java.util.Locale.ROOT)) {
            case "fabric":
                return resolveFabricVersions(minecraftVersion);
            case "neoforge":
                return resolveNeoForgeVersions(minecraftVersion);
            case "forge":
                return resolveForgeVersions(minecraftVersion);
            default:
                return new ArrayList<>();
        }
    }

    // ── Fabric ────────────────────────────────────────────────────────────────

    @NonNull
    private static ArrayList<LoaderVersionOption> resolveFabricVersions(
            @NonNull String minecraftVersion
    ) throws IOException {
        String url = String.format(java.util.Locale.ROOT, FABRIC_META_URL, minecraftVersion);
        String json = fetchJson(url);

        ArrayList<LoaderVersionOption> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                JSONObject loader = obj.optJSONObject("loader");
                if (loader == null) continue;
                String version = loader.optString("version", "");
                boolean stable = loader.optBoolean("stable", true);
                if (version.isEmpty()) continue;
                String label = stable ? version : version + " (beta)";
                result.add(new LoaderVersionOption(label, version));
            }
        } catch (Exception e) {
            throw new IOException("Failed to parse Fabric metadata: " + e.getMessage(), e);
        }
        return result;
    }

    // ── NeoForge ──────────────────────────────────────────────────────────────

    @NonNull
    private static ArrayList<LoaderVersionOption> resolveNeoForgeVersions(
            @NonNull String minecraftVersion
    ) throws IOException {
        String json = fetchJson(NEOFORGE_META_URL);
        ArrayList<LoaderVersionOption> result = new ArrayList<>();

        // Filter versions that start with the MC minor.patch (e.g. "21.1." for 1.21.1)
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
            throw new IOException("Failed to parse NeoForge metadata: " + e.getMessage(), e);
        }
        return result;
    }

    @Nullable
    private static String buildNeoForgePrefix(@NonNull String mcVersion) {
        String[] parts = mcVersion.split("\\.");
        if (parts.length < 2) return null;
        try {
            int major = Integer.parseInt(parts[0]);  // always 1
            int minor = Integer.parseInt(parts[1]);
            String patch = parts.length >= 3 ? parts[2] : "";
            // NeoForge uses e.g. "21.1." for MC 1.21.1
            return minor + (patch.isEmpty() ? "." : "." + patch + ".");
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ── Forge ─────────────────────────────────────────────────────────────────

    @NonNull
    private static ArrayList<LoaderVersionOption> resolveForgeVersions(
            @NonNull String minecraftVersion
    ) throws IOException {
        String url = "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json";
        String json = fetchJson(url);
        ArrayList<LoaderVersionOption> result = new ArrayList<>();

        try {
            JSONObject root  = new JSONObject(json);
            JSONObject promos = root.optJSONObject("promos");
            if (promos == null) return result;

            String recommended = promos.optString(minecraftVersion + "-recommended", null);
            String latest      = promos.optString(minecraftVersion + "-latest", null);

            if (recommended != null) {
                String fullVersion = minecraftVersion + "-" + recommended;
                result.add(new LoaderVersionOption(fullVersion + " (recommended)", fullVersion));
            }
            if (latest != null && !latest.equals(recommended)) {
                String fullVersion = minecraftVersion + "-" + latest;
                result.add(new LoaderVersionOption(fullVersion + " (latest)", fullVersion));
            }
        } catch (Exception e) {
            throw new IOException("Failed to parse Forge promotions: " + e.getMessage(), e);
        }
        return result;
    }

    // ── HTTP helper ───────────────────────────────────────────────────────────

    @NonNull
    private static String fetchJson(@NonNull String urlString) throws IOException {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(15_000);
            connection.setRequestProperty("Accept", "application/json");

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + responseCode + " from " + urlString);
            }

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            return sb.toString();
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}

