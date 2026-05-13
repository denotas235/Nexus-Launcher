package ca.dnamobile.javalauncher.logs;

import androidx.annotation.NonNull;

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
            cleaned = cleaned.substring(0, 1024) + "…";
        }
        return cleaned;
    }
}
