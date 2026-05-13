package ca.dnamobile.javalauncher.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Represents a single Minecraft version from the version manifest.
 */
public final class MinecraftVersion {

    @NonNull  public final String id;
    @NonNull  public final String type;
    @NonNull  public final String url;
    @Nullable public final String releaseTime;
    @Nullable public final String time;

    public MinecraftVersion(
            @NonNull  String id,
            @NonNull  String type,
            @NonNull  String url,
            @Nullable String releaseTime,
            @Nullable String time
    ) {
        this.id          = id;
        this.type        = type;
        this.url         = url;
        this.releaseTime = releaseTime;
        this.time        = time;
    }

    /** "release", "snapshot", "old_beta", or "old_alpha". */
    @NonNull
    public String getType() { return type; }

    @NonNull
    public String getId() { return id; }

    @NonNull
    public String getUrl() { return url; }

    @Nullable
    public String getReleaseTime() { return releaseTime; }

    @Override
    @NonNull
    public String toString() { return id; }
}
