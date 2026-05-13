package ca.dnamobile.javalauncher.auth;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import ca.dnamobile.javalauncher.data.AccountStore;

/**
 * Stub Microsoft auth manager.
 * Because MicrosoftAuthConfigPersonal.isConfigured() always returns false this
 * manager is never instantiated in production; all callback methods are no-ops.
 */
public final class MicrosoftAuthManagerPersonal {

    public interface Listener {
        void onSignInSuccess(@NonNull AccountStore.Account account);
        void onSignInFailed(@NonNull String message);
        void onSignOutComplete();
        void onAccountRefreshed(@Nullable AccountStore.Account account, @Nullable String errorMessage);
    }

    private final Context context;
    @Nullable
    private Listener listener;

    public MicrosoftAuthManagerPersonal(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    /** No-op: Microsoft auth is not configured. */
    public void signIn(@NonNull Listener listener) {
        this.listener = listener;
        listener.onSignInFailed("Microsoft login is not available in this build.");
    }

    /** No-op: Microsoft auth is not configured. */
    public void signOut() {
        if (listener != null) {
            listener.onSignOutComplete();
        }
    }

    /** No-op: Microsoft auth is not configured. */
    public void refreshMicrosoftAccount() {
        if (listener != null) {
            listener.onAccountRefreshed(null, "Microsoft login is not available in this build.");
        }
    }
}
