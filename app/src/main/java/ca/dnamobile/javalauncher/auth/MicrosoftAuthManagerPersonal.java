package ca.dnamobile.javalauncher.auth;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import ca.dnamobile.javalauncher.data.AccountStore;

/**
 * Stub Microsoft auth manager for the offline-only Nexus Launcher build.
 *
 * MicrosoftAuthConfigPersonal.isConfigured() always returns false, so signIn()
 * is never reachable from the UI. All callbacks are no-ops that report an
 * "unavailable" error through the listener.
 */
public final class MicrosoftAuthManagerPersonal {

    public interface Listener {
        void onSignedIn(@NonNull AccountStore.Account account);
        void onError(@NonNull String message);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final String NOT_AVAILABLE = "Microsoft login is not available in this build.";

    @SuppressWarnings("unused")
    private final Context context;
    @SuppressWarnings("unused")
    private final AccountStore accountStore;
    @Nullable
    private Listener listener;

    public MicrosoftAuthManagerPersonal(
            @NonNull Context context,
            @NonNull AccountStore accountStore
    ) {
        this.context = context.getApplicationContext();
        this.accountStore = accountStore;
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    /** No-op: Microsoft auth is not configured in this build. */
    public void signIn() {
        deliverError(NOT_AVAILABLE);
    }

    /** No-op: Microsoft auth is not configured in this build. */
    public void signOut() {
        // Nothing to do — no Microsoft account exists in offline mode.
    }

    /** No-op: Reports an error via the listener since MS auth is unavailable. */
    public void refreshMicrosoftAccount() {
        deliverError(NOT_AVAILABLE);
    }

    /** Releases any resources held by this manager. */
    public void dispose() {
        listener = null;
    }

    private void deliverError(@NonNull String message) {
        final Listener l = listener;
        if (l == null) return;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            l.onError(message);
        } else {
            MAIN.post(() -> l.onError(message));
        }
    }
}
