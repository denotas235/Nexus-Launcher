package ca.dnamobile.javalauncher.auth;

/**
 * Stub for Microsoft Auth configuration.
 * Returns false from isConfigured() so the launcher treats Microsoft login as
 * unavailable and falls through to the offline-account flow unconditionally.
 */
public final class MicrosoftAuthConfigPersonal {

    private MicrosoftAuthConfigPersonal() {}

    /**
     * Returns false → Microsoft login is disabled; offline play is always available.
     */
    public static boolean isConfigured() {
        return false;
    }
}
