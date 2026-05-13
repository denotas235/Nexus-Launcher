package ca.dnamobile.javalauncher.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Persistent account store with offline-first behaviour.
 *
 * The offline-account gate (hasMicrosoftLoginCompletedOnce) always returns true
 * so players never need to sign in to Microsoft to access offline play.
 */
public final class AccountStore {

    private static final String PREFS_NAME = "droidbridge_accounts";
    private static final String KEY_ACTIVE_ACCOUNT_ID = "active_account_id";
    private static final String KEY_OFFLINE_ACCOUNTS = "offline_accounts";
    private static final String KEY_LAST_MICROSOFT_ACCOUNT = "last_microsoft_account";
    private static final String KEY_MICROSOFT_LOGIN_DONE = "microsoft_login_completed_once";

    // ── Account model ─────────────────────────────────────────────────────────

    public static final class Account {
        public final String accountId;
        public final String username;
        /** Nullable – only present for Microsoft accounts with an active session. */
        @Nullable
        public final String minecraftAccessToken;
        private final boolean microsoftAccount;
        private final boolean hasMinecraftSession;

        public Account(
                @NonNull String accountId,
                @NonNull String username,
                @Nullable String minecraftAccessToken,
                boolean microsoftAccount,
                boolean hasMinecraftSession
        ) {
            this.accountId = accountId;
            this.username = username;
            this.minecraftAccessToken = minecraftAccessToken;
            this.microsoftAccount = microsoftAccount;
            this.hasMinecraftSession = hasMinecraftSession;
        }

        public static Account offlineAccount(@NonNull String username) {
            return new Account(
                    "offline-" + UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes()),
                    username,
                    null,
                    false,
                    false
            );
        }

        @NonNull
        public String getBestDisplayName() {
            return username;
        }

        public boolean isMicrosoftAccount() {
            return microsoftAccount;
        }

        public boolean hasMinecraftSession() {
            return hasMinecraftSession;
        }

        @NonNull
        JSONObject toJson() throws Exception {
            JSONObject obj = new JSONObject();
            obj.put("accountId", accountId);
            obj.put("username", username);
            if (minecraftAccessToken != null) obj.put("minecraftAccessToken", minecraftAccessToken);
            obj.put("microsoftAccount", microsoftAccount);
            obj.put("hasMinecraftSession", hasMinecraftSession);
            return obj;
        }

        @Nullable
        static Account fromJson(@NonNull JSONObject obj) {
            try {
                String accountId = obj.getString("accountId");
                String username  = obj.getString("username");
                String token     = obj.optString("minecraftAccessToken", null);
                boolean microsoft = obj.optBoolean("microsoftAccount", false);
                boolean session   = obj.optBoolean("hasMinecraftSession", false);
                return new Account(accountId, username, token, microsoft, session);
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    // ── Store ─────────────────────────────────────────────────────────────────

    private final SharedPreferences prefs;

    public AccountStore(@NonNull Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Always returns true — offline accounts are available without Microsoft login.
     * This bypasses the Microsoft-login gate in LauncherSettingsActivity.
     */
    public boolean hasMicrosoftLoginCompletedOnce() {
        return true;
    }

    /**
     * Load the currently active account.
     * Returns the first offline account if no explicit selection is persisted,
     * or creates a default "Player" account if none exist.
     */
    @Nullable
    public Account load() {
        String activeId = prefs.getString(KEY_ACTIVE_ACCOUNT_ID, null);

        List<Account> offlineAccounts = listOfflineAccounts();
        if (activeId != null) {
            for (Account a : offlineAccounts) {
                if (activeId.equals(a.accountId)) return a;
            }
        }

        // Fallback: first offline account
        if (!offlineAccounts.isEmpty()) return offlineAccounts.get(0);

        // No accounts at all: create a default offline account and persist it
        Account def = Account.offlineAccount("Player");
        saveOfflineAccount(def);
        setActiveAccount(def);
        return def;
    }

    /** Returns null — Microsoft accounts are not supported in this build. */
    @Nullable
    public Account loadLastMicrosoftAccount() {
        return null;
    }

    public void setActiveAccount(@NonNull Account account) {
        prefs.edit().putString(KEY_ACTIVE_ACCOUNT_ID, account.accountId).apply();
    }

    // ── Offline accounts ──────────────────────────────────────────────────────

    @NonNull
    public List<Account> listOfflineAccounts() {
        List<Account> list = new ArrayList<>();
        String json = prefs.getString(KEY_OFFLINE_ACCOUNTS, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                Account a = Account.fromJson(arr.getJSONObject(i));
                if (a != null) list.add(a);
            }
        } catch (Throwable ignored) {
        }
        return list;
    }

    public void saveOfflineAccount(@NonNull Account account) {
        List<Account> accounts = listOfflineAccounts();
        // Replace existing or add new
        boolean found = false;
        for (int i = 0; i < accounts.size(); i++) {
            if (accounts.get(i).accountId.equals(account.accountId)) {
                accounts.set(i, account);
                found = true;
                break;
            }
        }
        if (!found) accounts.add(account);
        persistOfflineAccounts(accounts);
    }

    public void deleteOfflineAccount(@NonNull String accountId) {
        List<Account> accounts = listOfflineAccounts();
        accounts.removeIf(a -> a.accountId.equals(accountId));
        persistOfflineAccounts(accounts);
        // If this was the active account, clear selection
        String activeId = prefs.getString(KEY_ACTIVE_ACCOUNT_ID, null);
        if (accountId.equals(activeId)) {
            prefs.edit().remove(KEY_ACTIVE_ACCOUNT_ID).apply();
        }
    }

    private void persistOfflineAccounts(@NonNull List<Account> accounts) {
        JSONArray arr = new JSONArray();
        for (Account a : accounts) {
            try {
                arr.put(a.toJson());
            } catch (Throwable ignored) {
            }
        }
        prefs.edit().putString(KEY_OFFLINE_ACCOUNTS, arr.toString()).apply();
    }
}
