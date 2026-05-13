package ca.dnamobile.javalauncher.logs;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import java.io.File;

/**
 * Manages the launcher log file lifecycle.
 */
public final class LauncherLogManager {

    private static final String PREFS_NAME   = "launcher_log_prefs";
    private static final String KEY_KEEP     = "keep_logs";
    private static final String LOG_FILENAME = "latest.log";

    private static volatile LauncherLogManager instance;

    @NonNull private final Context context;
    private boolean keepLogs;

    private LauncherLogManager(@NonNull Context context) {
        this.context  = context.getApplicationContext();
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

    // ── Static convenience methods ────────────────────────────────────────────

    public static boolean isKeepLogHistoryEnabled(@NonNull Context context) {
        return getInstance(context).isKeepingLogs();
    }

    public static void setKeepLogHistoryEnabled(@NonNull Context context, boolean enabled) {
        getInstance(context).setKeepLogs(enabled);
    }

    // ── Instance API ──────────────────────────────────────────────────────────

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

    @Nullable
    public File getLatestLogFile() {
        File logDir = new File(context.getFilesDir(), "logs");
        File log = new File(logDir, LOG_FILENAME);
        return log.isFile() ? log : null;
    }

    @NonNull
    public File getLogDirectory() {
        File logDir = new File(context.getFilesDir(), "logs");
        //noinspection ResultOfMethodCallIgnored
        logDir.mkdirs();
        return logDir;
    }

    public void cleanupIfNeeded() {
        if (keepLogs) return;
        File log = getLatestLogFile();
        if (log != null) {
            //noinspection ResultOfMethodCallIgnored
            log.delete();
        }
    }

    /**
     * Shares the latest log file using a system share sheet.
     *
     * @param context any Context — used to start the share Intent.
     */
    public static void shareLatestLog(@NonNull Context context) {
        LauncherLogManager mgr = getInstance(context);
        File logFile = mgr.getLatestLogFile();

        if (logFile == null || !logFile.isFile()) {
            android.widget.Toast.makeText(
                    context,
                    "No log file available to share.",
                    android.widget.Toast.LENGTH_SHORT
            ).show();
            return;
        }

        try {
            Uri uri = FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".fileprovider",
                    logFile
            );
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.putExtra(Intent.EXTRA_SUBJECT, "Launcher Log");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            Intent chooser = Intent.createChooser(intent, "Share log");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(chooser);
        } catch (Throwable throwable) {
            android.util.Log.e("LauncherLogManager", "Failed to share log", throwable);
            android.widget.Toast.makeText(
                    context,
                    "Unable to share log: " + throwable.getMessage(),
                    android.widget.Toast.LENGTH_LONG
            ).show();
        }
    }
}

