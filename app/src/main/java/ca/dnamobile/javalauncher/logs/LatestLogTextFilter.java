package ca.dnamobile.javalauncher.logs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Simple text filter that strips ANSI escape sequences and
 * trims overly long log lines for display in the in-game log overlay.
 */
public final class LatestLogTextFilter {

    private static final java.util.regex.Pattern ANSI_ESCAPE =
            java.util.regex.Pattern.compile("\u001B\\[[;\\d]*m");

    private LatestLogTextFilter() {}

    /**
     * Removes ANSI colour codes from a log line.
     */
    @NonNull
    public static String stripAnsi(@NonNull String line) {
        return ANSI_ESCAPE.matcher(line).replaceAll("");
    }

    /**
     * Cleans a raw log line for display.
     */
    @NonNull
    public static String filter(@NonNull String rawLine) {
        String cleaned = stripAnsi(rawLine);
        if (cleaned.length() > 1024) {
            cleaned = cleaned.substring(0, 1024) + "\u2026";
        }
        return cleaned;
    }

    /**
     * Cleans a raw real-time log line for dispatch to log listeners.
     * Returns null if the line should be suppressed entirely.
     *
     * Called by {@code net.kdt.pojavlaunch.Logger} for every line
     * produced by the native game process.
     */
    @Nullable
    public static String cleanRealtimeLine(@Nullable String rawLine) {
        if (rawLine == null) return null;
        String trimmed = rawLine.trim();
        if (trimmed.isEmpty()) return null;
        String cleaned = stripAnsi(trimmed);
        if (cleaned.length() > 2048) {
            cleaned = cleaned.substring(0, 2048) + "\u2026";
        }
        return cleaned;
    }
}

