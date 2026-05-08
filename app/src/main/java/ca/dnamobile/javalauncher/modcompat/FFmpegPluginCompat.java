package ca.dnamobile.javalauncher.modcompat;

/*
 * Copyright (c) 2026 DNA Mobile Applications.
 * All rights reserved.
 *
 * JavaLauncher FFmpeg companion-plugin discovery.
 */

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.Locale;

import ca.dnamobile.javalauncher.feature.log.Logging;

public final class FFmpegPluginCompat {
    private static final String TAG = "FFmpegPluginCompat";

    public static final String JAVALAUNCHER_PACKAGE = "ca.dnamobile.javalauncher.ffmpeg";

    private FFmpegPluginCompat() {
    }

    @NonNull
    public static Result discoverForReplayMod(@NonNull Context context, @NonNull File gameDirectory) {
        boolean replayModPresent = hasReplayMod(gameDirectory);
        if (!replayModPresent) {
            return new Result(
                    false,
                    false,
                    null,
                    null,
                    null,
                    "Replay Mod is not installed in this instance"
            );
        }

        Result result = discoverInstalled(context);
        return new Result(
                result.available,
                true,
                result.packageName,
                result.libraryPath,
                result.executablePath,
                result.errorMessage
        );
    }

    @NonNull
    public static Result discoverInstalled(@NonNull Context context) {
        Result result = discoverPackage(context, JAVALAUNCHER_PACKAGE);
        if (result.available) return result;

        return Result.missing(result.errorMessage != null
                ? result.errorMessage
                : "JavaLauncher FFmpeg plugin is not installed");
    }

    @NonNull
    private static Result discoverPackage(@NonNull Context context, @NonNull String packageName) {
        try {
            PackageManager manager = context.getPackageManager();
            PackageInfo info;
            if (Build.VERSION.SDK_INT >= 33) {
                info = manager.getPackageInfo(
                        packageName,
                        PackageManager.PackageInfoFlags.of(PackageManager.GET_SHARED_LIBRARY_FILES)
                );
            } else {
                //noinspection deprecation
                info = manager.getPackageInfo(packageName, PackageManager.GET_SHARED_LIBRARY_FILES);
            }

            ApplicationInfo applicationInfo = info.applicationInfo;
            if (applicationInfo == null || applicationInfo.nativeLibraryDir == null) {
                return Result.missing("FFmpeg plugin has no nativeLibraryDir: " + packageName);
            }

            File libraryDir = new File(applicationInfo.nativeLibraryDir);
            File executable = new File(libraryDir, "libffmpeg.so");
            if (!executable.isFile()) {
                return Result.missing("FFmpeg executable missing: " + executable.getAbsolutePath());
            }

            // Best effort only. Android normally extracts native libs executable already.
            //noinspection ResultOfMethodCallIgnored
            executable.setExecutable(true, false);

            Logging.i(TAG, "Discovered JavaLauncher FFmpeg plugin"
                    + " package=" + packageName
                    + " libraryPath=" + libraryDir.getAbsolutePath()
                    + " executable=" + executable.getAbsolutePath());

            return new Result(
                    true,
                    false,
                    packageName,
                    libraryDir.getAbsolutePath(),
                    executable.getAbsolutePath(),
                    null
            );
        } catch (Throwable throwable) {
            Logging.e(TAG, "JavaLauncher FFmpeg plugin not available: " + packageName, throwable);
            return Result.missing(throwable.toString());
        }
    }

    public static boolean hasReplayMod(@NonNull File gameDirectory) {
        File modsDir = new File(gameDirectory, "mods");
        if (!modsDir.isDirectory()) return false;

        File[] files = modsDir.listFiles();
        if (files == null) return false;

        for (File file : files) {
            if (file == null || !file.isFile()) continue;
            String name = file.getName().toLowerCase(Locale.ROOT);
            if ((name.endsWith(".jar") || name.endsWith(".disabled"))
                    && (name.contains("replaymod")
                    || name.contains("replay-mod")
                    || name.contains("replay_mod"))) {
                return true;
            }
        }
        return false;
    }

    public static final class Result {
        public final boolean available;
        public final boolean replayModPresent;
        @Nullable public final String packageName;
        @Nullable public final String libraryPath;
        @Nullable public final String executablePath;
        @Nullable public final String errorMessage;

        private Result(
                boolean available,
                boolean replayModPresent,
                @Nullable String packageName,
                @Nullable String libraryPath,
                @Nullable String executablePath,
                @Nullable String errorMessage
        ) {
            this.available = available;
            this.replayModPresent = replayModPresent;
            this.packageName = packageName;
            this.libraryPath = libraryPath;
            this.executablePath = executablePath;
            this.errorMessage = errorMessage;
        }

        @NonNull
        static Result missing(@Nullable String errorMessage) {
            return new Result(false, false, null, null, null, errorMessage);
        }
    }
}
