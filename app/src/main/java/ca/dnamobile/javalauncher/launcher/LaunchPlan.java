package ca.dnamobile.javalauncher.launcher;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;

import ca.dnamobile.javalauncher.data.AccountStore;
import ca.dnamobile.javalauncher.instance.LauncherInstance;
import ca.dnamobile.javalauncher.utils.path.PathManager;

/**
 * Immutable description of everything needed to start a Minecraft game session.
 * Built by the game activity before handing off to the native launcher.
 */
public final class LaunchPlan {

    @NonNull  public final LauncherInstance instance;
    @NonNull  public final AccountStore.Account account;
    @NonNull  public final String resolvedRenderer;
    @NonNull  public final String resolvedRuntime;
    public final int resolvedRamMb;
    @Nullable public final String extraJvmArgs;

    public LaunchPlan(
            @NonNull  LauncherInstance instance,
            @NonNull  AccountStore.Account account,
            @NonNull  String resolvedRenderer,
            @NonNull  String resolvedRuntime,
            int resolvedRamMb,
            @Nullable String extraJvmArgs
    ) {
        this.instance         = instance;
        this.account          = account;
        this.resolvedRenderer = resolvedRenderer;
        this.resolvedRuntime  = resolvedRuntime;
        this.resolvedRamMb    = resolvedRamMb;
        this.extraJvmArgs     = extraJvmArgs;
    }

    /** Returns true if this is an offline (non-Microsoft) launch. */
    public boolean isOffline() {
        return !account.isMicrosoftAccount();
    }

    /**
     * Username argument passed to Minecraft.
     * For offline: the username stored in the account.
     */
    @NonNull
    public String getMinecraftUsername() {
        return account.getBestDisplayName();
    }

    /**
     * Access token passed to Minecraft via --accessToken.
     * Offline sessions use the placeholder token "0".
     */
    @NonNull
    public String getMinecraftAccessToken() {
        if (account.minecraftAccessToken != null && !account.minecraftAccessToken.isEmpty()) {
            return account.minecraftAccessToken;
        }
        return "0";
    }

    /**
     * UUID argument passed to Minecraft via --uuid.
     * Offline sessions derive the UUID from the username (same as vanilla offline).
     */
    @NonNull
    public String getMinecraftUuid() {
        return account.accountId.replace("-", "").toLowerCase(java.util.Locale.US);
    }

    /**
     * Returns the Minecraft game directory for this launch.
     * Delegates to the underlying {@link LauncherInstance}.
     */
    @NonNull
    public File getGameDirectory() {
        return instance.getGameDirectory();
    }

    /**
     * Returns the Java runtime home directory for this launch.
     *
     * The directory is resolved from {@link PathManager#DIR_MULTIRT_HOME}
     * and the {@code resolvedRuntime} identifier set at plan construction.
     * Falls back to {@code PathManager.DIR_MULTIRT_HOME/resolvedRuntime}
     * if PathManager has not been initialised yet.
     */
    @NonNull
    public File getRuntimeDirectory() {
        String multiRtHome = PathManager.DIR_MULTIRT_HOME;
        if (multiRtHome != null && !multiRtHome.trim().isEmpty()) {
            return new File(multiRtHome, resolvedRuntime);
        }
        // Fallback before PathManager.initContextConstants() has been called
        return new File("runtimes", resolvedRuntime);
    }
}

