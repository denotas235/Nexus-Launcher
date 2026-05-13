/*
 * Copyright (c) 2026 DNA Mobile Applications.
 * All rights reserved.
 *
 * This file is DroidBridge project code.
 */
package ca.dnamobile.javalauncher.game;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import ca.dnamobile.javalauncher.feature.log.Logging;
import ca.dnamobile.javalauncher.utils.path.PathManager;

/**
 * Builds the JVM + game argument array needed by {@link com.oracle.dalvik.VMLauncher#launchJVM}.
 *
 * Reads the installed Minecraft version JSON (Mojang launcher format) and builds
 * a complete argument list for offline play. Returns null if the version is not
 * installed so the caller can show an appropriate error.
 *
 * Handles:
 * - Mojang version JSON v21 (arguments object) and legacy (minecraftArguments string)
 * - Inheritance resolution (Forge / Fabric / Quilt / NeoForge loader profiles)
 * - Library path extraction via downloads.artifact or Maven coordinate fallback
 * - OS rule filtering (Android treated as Linux)
 */
public final class MinecraftArgBuilder {

    private static final String TAG = "MinecraftArgBuilder";

    private MinecraftArgBuilder() {}

    /**
     * Returns the full argument array for {@link com.oracle.dalvik.VMLauncher#launchJVM},
     * or null if the version is not installed (version JSON missing).
     */
    @Nullable
    public static String[] buildArgs(
            @NonNull String gameDirectory,
            @NonNull String runtimeName,
            int ramMb,
            @NonNull String username,
            @NonNull String accessToken,
            @NonNull String uuid,
            @NonNull String versionId,
            @Nullable String extraJvmArgs
    ) {
        File gameDir     = new File(gameDirectory);
        File versionDir  = new File(gameDir, "versions/" + versionId);
        File versionJson = new File(versionDir, versionId + ".json");

        if (!versionJson.exists()) {
            Logging.w(TAG, "Version JSON not found: " + versionJson.getAbsolutePath());
            return null;
        }

        JSONObject versionData;
        try {
            versionData = readJson(versionJson);
        } catch (Exception e) {
            Logging.e(TAG, "Failed to read version JSON", e);
            return null;
        }

        try {
            versionData = resolveInheritance(gameDir, versionData);
        } catch (Exception e) {
            Logging.w(TAG, "Inheritance resolution failed — proceeding with base only", e);
        }

        // Runtime directory
        String runtimeHome = PathManager.DIR_MULTIRT_HOME;
        File runtimeDir = (runtimeHome != null && !runtimeHome.trim().isEmpty())
                ? new File(runtimeHome, runtimeName)
                : new File("runtimes", runtimeName);

        // Classpath
        String classpath = buildClasspath(gameDir, versionId, versionData);
        if (classpath == null || classpath.isEmpty()) {
            Logging.e(TAG, "Empty classpath — version files not fully downloaded");
            return null;
        }

        // Version metadata
        String mainClass  = versionData.optString("mainClass", "net.minecraft.client.main.Main");
        String assetIndex = "legacy";
        JSONObject assetIndexObj = versionData.optJSONObject("assetIndex");
        if (assetIndexObj != null) {
            assetIndex = assetIndexObj.optString("id", "legacy");
        }
        String assetsDir = new File(gameDir, "assets").getAbsolutePath();

        // Natives directory (may be empty for modern versions that bundle natives in jars)
        File nativesDir = new File(versionDir, versionId + "-natives");
        if (!nativesDir.exists()) nativesDir.mkdirs();

        List<String> args = new ArrayList<>();

        // ── JVM args ──────────────────────────────────────────────────────────
        args.add(new File(runtimeDir, "bin/java").getAbsolutePath());

        args.add("-Xms512m");
        args.add("-Xmx" + Math.max(512, ramMb) + "m");
        args.add("-XX:+UnlockExperimentalVMOptions");
        args.add("-XX:+UseG1GC");
        args.add("-XX:G1NewSizePercent=20");
        args.add("-XX:G1ReservePercent=20");
        args.add("-XX:MaxGCPauseMillis=50");
        args.add("-XX:G1HeapRegionSize=32M");
        args.add("-Dfml.ignoreInvalidMinecraftCertificates=true");
        args.add("-Dfml.ignorePatchDiscrepancies=true");
        args.add("-Dlog4j2.formatMsgNoLookups=true");
        args.add("-Djava.library.path=" + nativesDir.getAbsolutePath());
        args.add("-cp");
        args.add(classpath);

        // Extra user-defined JVM args
        if (extraJvmArgs != null && !extraJvmArgs.trim().isEmpty()) {
            for (String a : extraJvmArgs.trim().split("\\s+")) {
                if (!a.isEmpty()) args.add(a);
            }
        }

        // ── Main class ────────────────────────────────────────────────────────
        args.add(mainClass);

        // ── Game args ─────────────────────────────────────────────────────────
        // Handle both modern (arguments.game array) and legacy (minecraftArguments string) formats
        JSONObject arguments = versionData.optJSONObject("arguments");
        if (arguments != null) {
            JSONArray gameArgs = arguments.optJSONArray("game");
            if (gameArgs != null) {
                appendModernGameArgs(gameArgs, args, username, accessToken, uuid,
                        versionId, gameDir.getAbsolutePath(), assetsDir, assetIndex);
            } else {
                appendDefaultGameArgs(args, username, accessToken, uuid,
                        versionId, gameDir.getAbsolutePath(), assetsDir, assetIndex);
            }
        } else {
            String legacyArgs = versionData.optString("minecraftArguments", null);
            if (legacyArgs != null) {
                appendLegacyGameArgs(legacyArgs, args, username, accessToken, uuid,
                        versionId, gameDir.getAbsolutePath(), assetsDir, assetIndex);
            } else {
                appendDefaultGameArgs(args, username, accessToken, uuid,
                        versionId, gameDir.getAbsolutePath(), assetsDir, assetIndex);
            }
        }

        Logging.i(TAG, "Built " + args.size() + " args for version " + versionId
                + " user=" + username + " offline=" + "0".equals(accessToken));
        return args.toArray(new String[0]);
    }

    // ── Game argument builders ────────────────────────────────────────────────

    private static void appendModernGameArgs(
            @NonNull JSONArray gameArgs,
            @NonNull List<String> out,
            @NonNull String username,
            @NonNull String accessToken,
            @NonNull String uuid,
            @NonNull String versionId,
            @NonNull String gameDir,
            @NonNull String assetsDir,
            @NonNull String assetIndex
    ) {
        for (int i = 0; i < gameArgs.length(); i++) {
            Object item = gameArgs.opt(i);
            if (item instanceof String) {
                out.add(substituteVars((String) item, username, accessToken, uuid,
                        versionId, gameDir, assetsDir, assetIndex));
            }
            // Conditional rule objects (JSONObject) are skipped — we use defaults
        }
    }

    private static void appendLegacyGameArgs(
            @NonNull String legacyArgs,
            @NonNull List<String> out,
            @NonNull String username,
            @NonNull String accessToken,
            @NonNull String uuid,
            @NonNull String versionId,
            @NonNull String gameDir,
            @NonNull String assetsDir,
            @NonNull String assetIndex
    ) {
        for (String token : legacyArgs.split(" ")) {
            if (!token.isEmpty()) {
                out.add(substituteVars(token, username, accessToken, uuid,
                        versionId, gameDir, assetsDir, assetIndex));
            }
        }
    }

    private static void appendDefaultGameArgs(
            @NonNull List<String> out,
            @NonNull String username,
            @NonNull String accessToken,
            @NonNull String uuid,
            @NonNull String versionId,
            @NonNull String gameDir,
            @NonNull String assetsDir,
            @NonNull String assetIndex
    ) {
        out.add("--username");    out.add(username);
        out.add("--version");     out.add(versionId);
        out.add("--gameDir");     out.add(gameDir);
        out.add("--assetsDir");   out.add(assetsDir);
        out.add("--assetIndex");  out.add(assetIndex);
        out.add("--uuid");        out.add(uuid);
        out.add("--accessToken"); out.add(accessToken);
        out.add("--userType");    out.add("legacy");
        out.add("--versionType"); out.add("release");
    }

    @NonNull
    private static String substituteVars(
            @NonNull String template,
            @NonNull String username,
            @NonNull String accessToken,
            @NonNull String uuid,
            @NonNull String versionId,
            @NonNull String gameDir,
            @NonNull String assetsDir,
            @NonNull String assetIndex
    ) {
        return template
                .replace("${auth_player_name}", username)
                .replace("${auth_uuid}", uuid)
                .replace("${auth_access_token}", accessToken)
                .replace("${auth_session}", accessToken)
                .replace("${user_type}", "legacy")
                .replace("${version_name}", versionId)
                .replace("${game_directory}", gameDir)
                .replace("${assets_root}", assetsDir)
                .replace("${game_assets}", assetsDir)
                .replace("${assets_index_name}", assetIndex)
                .replace("${version_type}", "release")
                .replace("${user_properties}", "{}");
    }

    // ── Classpath builder ─────────────────────────────────────────────────────

    @Nullable
    private static String buildClasspath(
            @NonNull File gameDir,
            @NonNull String versionId,
            @NonNull JSONObject versionData
    ) {
        StringBuilder cp = new StringBuilder();
        File libsDir = new File(gameDir, "libraries");

        JSONArray libraries = versionData.optJSONArray("libraries");
        if (libraries != null) {
            for (int i = 0; i < libraries.length(); i++) {
                JSONObject lib = libraries.optJSONObject(i);
                if (lib == null || !checkRules(lib)) continue;

                String libPath = getLibraryArtifactPath(lib);
                if (libPath == null) continue;

                File libFile = new File(libsDir, libPath);
                if (libFile.exists()) {
                    if (cp.length() > 0) cp.append(:);
                    cp.append(libFile.getAbsolutePath());
                }
            }
        }

        // Version JAR
        File versionJar = new File(gameDir, "versions/" + versionId + "/" + versionId + ".jar");
        if (versionJar.exists()) {
            if (cp.length() > 0) cp.append(:);
            cp.append(versionJar.getAbsolutePath());
        }

        return cp.toString();
    }

    @Nullable
    private static String getLibraryArtifactPath(@NonNull JSONObject lib) {
        // Prefer explicit artifact path from downloads block
        JSONObject downloads = lib.optJSONObject("downloads");
        if (downloads != null) {
            JSONObject artifact = downloads.optJSONObject("artifact");
            if (artifact != null) {
                String path = artifact.optString("path", null);
                if (path != null && !path.isEmpty()) return path;
            }
        }
        // Derive from Maven coordinate
        String name = lib.optString("name", null);
        if (name == null || name.isEmpty()) return null;
        return mavenCoordToRelativePath(name);
    }

    @NonNull
    private static String mavenCoordToRelativePath(@NonNull String coord) {
        // groupId:artifactId:version[:classifier][@extension]
        String[] parts = coord.split(":");
        if (parts.length < 3) return "";
        String group    = parts[0].replace(., /);
        String artifact = parts[1];
        String version  = parts[2];
        String classifier = (parts.length > 3) ? "-" + parts[3] : "";
        return group + "/" + artifact + "/" + version
                + "/" + artifact + "-" + version + classifier + ".jar";
    }

    /** Returns true if the library should be included on Android (treated as Linux). */
    private static boolean checkRules(@NonNull JSONObject lib) {
        JSONArray rules = lib.optJSONArray("rules");
        if (rules == null || rules.length() == 0) return true;

        boolean result = false;
        for (int i = 0; i < rules.length(); i++) {
            JSONObject rule = rules.optJSONObject(i);
            if (rule == null) continue;
            boolean allow = "allow".equals(rule.optString("action", "disallow"));
            JSONObject os = rule.optJSONObject("os");
            if (os == null) {
                result = allow; // no OS restriction
            } else {
                String osName = os.optString("name", "");
                // Treat Android as Linux; exclude Windows/macOS specific libs
                if ("osx".equals(osName) || "windows".equals(osName)) {
                    if (allow) return false; // allowed only on other platform
                } else {
                    result = allow;
                }
            }
        }
        return result;
    }

    // ── Inheritance resolver ──────────────────────────────────────────────────

    @NonNull
    private static JSONObject resolveInheritance(
            @NonNull File gameDir,
            @NonNull JSONObject version
    ) throws Exception {
        String inheritsFrom = version.optString("inheritsFrom", null);
        if (inheritsFrom == null || inheritsFrom.isEmpty()) return version;

        File parentJson = new File(gameDir, "versions/" + inheritsFrom + "/" + inheritsFrom + ".json");
        if (!parentJson.exists()) {
            Logging.w(TAG, "Parent version JSON not found: " + parentJson.getAbsolutePath());
            return version;
        }

        JSONObject parent = resolveInheritance(gameDir, readJson(parentJson));
        JSONObject merged = new JSONObject();

        // Start with parent fields
        for (Iterator<String> it = parent.keys(); it.hasNext(); ) {
            String k = it.next();
            merged.put(k, parent.get(k));
        }

        // Override / merge child fields
        for (Iterator<String> it = version.keys(); it.hasNext(); ) {
            String k = it.next();
            if ("inheritsFrom".equals(k)) continue;
            if ("libraries".equals(k)) {
                JSONArray childLibs  = version.optJSONArray("libraries");
                JSONArray parentLibs = parent.optJSONArray("libraries");
                JSONArray mergedLibs = new JSONArray();
                if (childLibs  != null) for (int i = 0; i < childLibs.length();  i++) mergedLibs.put(childLibs.get(i));
                if (parentLibs != null) for (int i = 0; i < parentLibs.length(); i++) mergedLibs.put(parentLibs.get(i));
                merged.put("libraries", mergedLibs);
            } else if ("arguments".equals(k)) {
                // Merge arguments objects
                JSONObject childArgs  = version.optJSONObject("arguments");
                JSONObject parentArgs = parent.optJSONObject("arguments");
                if (childArgs != null && parentArgs != null) {
                    JSONObject mergedArgs = new JSONObject();
                    for (String argKey : new String[]{"jvm", "game"}) {
                        JSONArray ca = childArgs.optJSONArray(argKey);
                        JSONArray pa = parentArgs.optJSONArray(argKey);
                        JSONArray ma = new JSONArray();
                        if (pa != null) for (int i = 0; i < pa.length(); i++) ma.put(pa.get(i));
                        if (ca != null) for (int i = 0; i < ca.length(); i++) ma.put(ca.get(i));
                        mergedArgs.put(argKey, ma);
                    }
                    merged.put("arguments", mergedArgs);
                } else {
                    merged.put(k, version.get(k));
                }
            } else {
                merged.put(k, version.get(k));
            }
        }
        return merged;
    }

    @NonNull
    private static JSONObject readJson(@NonNull File file) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append(n);
        }
        return new JSONObject(sb.toString());
    }
}
