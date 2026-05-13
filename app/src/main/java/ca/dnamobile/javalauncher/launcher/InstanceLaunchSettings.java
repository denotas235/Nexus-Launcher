package ca.dnamobile.javalauncher.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Per-instance overrides for renderer, Java runtime, custom JVM args, and RAM.
 *
 * Each instance stores its overrides under a unique string key in SharedPreferences.
 * Use RENDERER_DEFAULT and RAM_DEFAULT as sentinel "not overridden" values.
 */
public final class InstanceLaunchSettings {

    public static final String RENDERER_DEFAULT = "";
    public static final int    RAM_DEFAULT       = -1;

    private static final String PREFS_NAME = "instance_launch_settings";
    private static final String SUFFIX_RENDERER = ":renderer";
    private static final String SUFFIX_RUNTIME  = ":runtime";
    private static final String SUFFIX_JVM_ARGS = ":jvmArgs";
    private static final String SUFFIX_RAM       = ":ram";

    // ── Runtime catalogue ─────────────────────────────────────────────────────

    private static final String[] RUNTIME_NAMES  = { "", "java-8-openjdk", "java-11-openjdk", "java-17-openjdk", "java-21-openjdk" };
    private static final String[] RUNTIME_LABELS = { "Default (auto)", "Java 8", "Java 11", "Java 17", "Java 21" };

    @NonNull
    public static String[] getRuntimeDisplayLabels() {
        return RUNTIME_LABELS.clone();
    }

    public static int runtimeIndexForName(@Nullable String name) {
        if (name == null || name.trim().isEmpty()) return 0;
        for (int i = 0; i < RUNTIME_NAMES.length; i++) {
            if (RUNTIME_NAMES[i].equals(name)) return i;
        }
        return 0;
    }

    @NonNull
    public static String runtimeNameForIndex(int index) {
        if (index < 0 || index >= RUNTIME_NAMES.length) return "";
        return RUNTIME_NAMES[index];
    }

    // ── Key resolution ────────────────────────────────────────────────────────

    /**
     * Resolves a stable SharedPreferences key for an instance.
     *
     * If {@code preferredKey} is non-null and non-empty it is used directly.
     * Otherwise {@code fallbackKey} is returned. The result is never null.
     *
     * This allows callers to prefer a stable instance ID while gracefully
     * falling back to a display name or other identifier when the ID is absent.
     *
     * @param preferredKey the preferred (usually stable) key; may be null or empty
     * @param fallbackKey  the fallback key used when preferredKey is absent
     * @return a non-null, non-empty key suitable for SharedPreferences lookup
     */
    @NonNull
    public static String resolveInstanceKey(
            @Nullable String preferredKey,
            @Nullable String fallbackKey
    ) {
        if (preferredKey != null && !preferredKey.trim().isEmpty()) {
            return preferredKey.trim();
        }
        if (fallbackKey != null && !fallbackKey.trim().isEmpty()) {
            return fallbackKey.trim();
        }
        return "default";
    }

    // ── Settings data class ───────────────────────────────────────────────────

    public static final class Settings {
        @NonNull
        public String rendererIdentifier = RENDERER_DEFAULT;
        @NonNull
        public String runtimeName = "";
        @Nullable
        public String customJvmArgs = null;
        public int ramMb = RAM_DEFAULT;

        public boolean hasRamOverride() {
            return ramMb != RAM_DEFAULT && ramMb > 0;
        }
    }

    // ── Load / save / clear ───────────────────────────────────────────────────

    @NonNull
    public static Settings load(@NonNull Context context, @NonNull String key) {
        SharedPreferences prefs = prefs(context);
        Settings s = new Settings();
        s.rendererIdentifier = prefs.getString(key + SUFFIX_RENDERER, RENDERER_DEFAULT);
        s.runtimeName        = prefs.getString(key + SUFFIX_RUNTIME,  "");
        s.customJvmArgs      = prefs.getString(key + SUFFIX_JVM_ARGS, null);
        s.ramMb              = prefs.getInt(key + SUFFIX_RAM, RAM_DEFAULT);
        if (s.rendererIdentifier == null) s.rendererIdentifier = RENDERER_DEFAULT;
        return s;
    }

    public static void save(@NonNull Context context, @NonNull String key, @NonNull Settings settings) {
        prefs(context).edit()
                .putString(key + SUFFIX_RENDERER, settings.rendererIdentifier)
                .putString(key + SUFFIX_RUNTIME,  settings.runtimeName)
                .putString(key + SUFFIX_JVM_ARGS, settings.customJvmArgs)
                .putInt(key    + SUFFIX_RAM,       settings.ramMb)
                .apply();
    }

    public static void clear(@NonNull Context context, @NonNull String key) {
        prefs(context).edit()
                .remove(key + SUFFIX_RENDERER)
                .remove(key + SUFFIX_RUNTIME)
                .remove(key + SUFFIX_JVM_ARGS)
                .remove(key + SUFFIX_RAM)
                .apply();
    }

    @NonNull
    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private InstanceLaunchSettings() {}
}

