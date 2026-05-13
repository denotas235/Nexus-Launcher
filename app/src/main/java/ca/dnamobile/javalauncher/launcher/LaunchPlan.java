package ca.dnamobile.javalauncher.launcher;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import ca.dnamobile.javalauncher.data.AccountStore;
import ca.dnamobile.javalauncher.instance.LauncherInstance;

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
}
