package ca.dnamobile.javalauncher.launcher;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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

        // Snapshots and betas get the latest
        if (minecraftVersionId.startsWith("b") || minecraftVersionId.startsWith("a")) {
            return "java-8-openjdk";
        }

        // Try to parse major.minor
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

    /**
     * Returns a human-readable display label for a runtime identifier.
     */
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
}
