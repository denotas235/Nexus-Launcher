package ca.dnamobile.javalauncher.logs;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;

/**
 * Manages the launcher log file lifecycle.
 *
 * When keepLogs is enabled the most recent game session log is retained on disk
 * so the user can share it for bug reports. Otherwise it is deleted on the next
 * launch.
 */
public final class LauncherLogManager {

    private static final String PREFS_NAME  = "launcher_log_prefs";
    private static final String KEY_KEEP    = "keep_logs";
    private static final String LOG_FILENAME = "latest.log";

    private static volatile LauncherLogManager instance;

    @NonNull
    private final Context context;
    private boolean keepLogs;

    private LauncherLogManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
        SharedPreferences prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.keepLogs = prefs.getBoolean(KEY_KEEP, false);
    }

    @NonNull
    public static LauncherLogManager getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (LauncherLogManager.class) {
                if (instance == null) {
                    instance = new LauncherLogManager(context);
                }
            }
        }
        return instance;
    }

    public boolean isKeepingLogs() {
        return keepLogs;
    }

    public void setKeepLogs(boolean keep) {
        this.keepLogs = keep;
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_KEEP, keep)
                .apply();
    }

    /** Returns the latest log file, or null if none exists. */
    @Nullable
    public File getLatestLogFile() {
        File logDir = new File(context.getFilesDir(), "logs");
        File log = new File(logDir, LOG_FILENAME);
        return log.isFile() ? log : null;
    }

    /** Returns the directory where log files are stored. */
    @NonNull
    public File getLogDirectory() {
        File logDir = new File(context.getFilesDir(), "logs");
        //noinspection ResultOfMethodCallIgnored
        logDir.mkdirs();
        return logDir;
    }

    /** Deletes the latest log file unless keep-logs is enabled. */
    public void cleanupIfNeeded() {
        if (keepLogs) return;
        File log = getLatestLogFile();
        if (log != null) {
            //noinspection ResultOfMethodCallIgnored
            log.delete();
        }
    }
}
