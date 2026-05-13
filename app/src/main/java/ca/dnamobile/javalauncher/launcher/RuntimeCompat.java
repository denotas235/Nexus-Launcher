package ca.dnamobile.javalauncher.launcher;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;

/**
 * Resolves the appropriate Java runtime for a given Minecraft version.
 *
 * Minecraft Java Edition has used different Java versions across its history:
 *  - 1.16.5 and older  → Java 8
 *  - 1.17 – 1.17.1     → Java 16
 *  - 1.18+             → Java 17
 *  - 1.21+             → Java 21
 */
public final class RuntimeCompat {

    /** Build-time patch identifier included in runtime unpack logs. */
    public static final String PATCH_ID = "droidbridge-r1";

    private RuntimeCompat() {}

    /**
     * Returns the recommended Java runtime identifier for the given Minecraft version string.
     * Returns null to indicate "use whatever the launcher default is".
     */
    @Nullable
    public static String resolveRuntimeForVersion(@Nullable String minecraftVersionId) {
        if (minecraftVersionId == null || minecraftVersionId.trim().isEmpty()) {
            return null;
        }
        if (minecraftVersionId.startsWith("b") || minecraftVersionId.startsWith("a")) {
            return "java-8-openjdk";
        }
        String[] parts = minecraftVersionId.split("[.\\-]");
        if (parts.length < 2) return null;
        try {
            int minor = Integer.parseInt(parts[1]);
            if (minor <= 16) return "java-8-openjdk";
            if (minor <= 17) return "java-17-openjdk";
            if (minor <= 20) return "java-17-openjdk";
            return "java-21-openjdk";
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Returns a human-readable display label for a runtime identifier. */
    @NonNull
    public static String displayLabel(@Nullable String runtimeName) {
        if (runtimeName == null || runtimeName.trim().isEmpty()) return "Auto";
        switch (runtimeName) {
            case "java-8-openjdk":  return "Java 8";
            case "java-11-openjdk": return "Java 11";
            case "java-17-openjdk": return "Java 17";
            case "java-21-openjdk": return "Java 21";
            default: return runtimeName;
        }
    }

    /**
     * Returns the Java major version number for a runtime name.
     * "java-8-openjdk" → 8, "java-17-openjdk" → 17, etc.
     */
    public static int javaMajorForRuntimeName(@Nullable String runtimeName) {
        if (runtimeName == null) return 8;
        if (runtimeName.contains("-8-"))  return 8;
        if (runtimeName.contains("-11-")) return 11;
        if (runtimeName.contains("-17-")) return 17;
        if (runtimeName.contains("-21-")) return 21;
        // Try to parse trailing number
        String[] parts = runtimeName.split("[-_]");
        for (int i = parts.length - 1; i >= 0; i--) {
            try {
                int v = Integer.parseInt(parts[i]);
                if (v >= 8 && v <= 99) return v;
            } catch (NumberFormatException ignored) {}
        }
        return 8;
    }

    /**
     * Returns true if the named runtime appears to be correctly installed.
     *
     * A runtime is considered usable when its home directory exists and contains
     * either a libjvm.so (JVM native library) or a bin/java binary, matching the
     * expected Java major version if available.
     */
    public static boolean isRuntimeInstalledForJava(
            @Nullable String jreName,
            @Nullable File runtimeHome,
            int javaMajor
    ) {
        if (runtimeHome == null || !runtimeHome.isDirectory()) return false;

        // JVM native library (all Android runtimes)
        File libjvmArm64   = new File(runtimeHome, "lib/server/libjvm.so");
        File libjvmArm     = new File(runtimeHome, "lib/arm/server/libjvm.so");
        File libjvmGeneric = new File(runtimeHome, "lib/libjvm.so");

        // Java 8 uses rt.jar instead of modules
        if (javaMajor <= 8) {
            boolean hasRtJar = new File(runtimeHome, "lib/rt.jar").isFile();
            boolean hasJvm   = libjvmArm64.isFile() || libjvmArm.isFile() || libjvmGeneric.isFile();
            return hasRtJar && hasJvm;
        }

        // Java 11+ uses modules
        boolean hasModules = new File(runtimeHome, "lib/modules").isFile();
        boolean hasJvm     = libjvmArm64.isFile() || libjvmArm.isFile() || libjvmGeneric.isFile();
        return hasModules && hasJvm;
    }

    /**
     * Returns a short human-readable description of the runtime installation state.
     * Used in log messages to diagnose runtime problems.
     */
    @NonNull
    public static String describeRuntimeState(
            @Nullable String jreName,
            @Nullable File runtimeHome
    ) {
        if (runtimeHome == null) return "runtimeHome=null";
        if (!runtimeHome.exists()) return "dir=missing path=" + runtimeHome.getAbsolutePath();

        StringBuilder sb = new StringBuilder();
        sb.append("path=").append(runtimeHome.getAbsolutePath());

        File libjvm = new File(runtimeHome, "lib/server/libjvm.so");
        sb.append(" libjvm=").append(libjvm.isFile() ? "ok" : "missing");

        File rtJar   = new File(runtimeHome, "lib/rt.jar");
        File modules = new File(runtimeHome, "lib/modules");
        if (rtJar.isFile())   sb.append(" rt.jar=ok");
        if (modules.isFile()) sb.append(" modules=ok");

        return sb.toString();
    }
}

