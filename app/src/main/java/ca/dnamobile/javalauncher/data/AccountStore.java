package ca.dnamobile.javalauncher.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
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

    // ── Account model ─────────────────────────────────────────────────────────

    public static final class Account {
        public final String accountId;
        public final String username;
        /** Nullable – only present for Microsoft accounts with an active session. */
        @Nullable
        public final String minecraftAccessToken;
        /** Nullable – Microsoft CDN skin URL for Microsoft accounts. */
        @Nullable
        public final String skinUrl;
        /** Nullable – absolute path to local skin PNG file for offline accounts. */
        @Nullable
        public final String offlineSkinPath;
        /** Nullable – "classic" or "slim" for offline accounts with a custom skin. */
        @Nullable
        public final String offlineSkinModel;

        private final boolean microsoftAccount;
        private final boolean hasMinecraftSession;

        public Account(
                @NonNull String accountId,
                @NonNull String username,
                @Nullable String minecraftAccessToken,
                @Nullable String skinUrl,
                @Nullable String offlineSkinPath,
                @Nullable String offlineSkinModel,
                boolean microsoftAccount,
                boolean hasMinecraftSession
        ) {
            this.accountId = accountId;
            this.username = username;
            this.minecraftAccessToken = minecraftAccessToken;
            this.skinUrl = skinUrl;
            this.offlineSkinPath = offlineSkinPath;
            this.offlineSkinModel = offlineSkinModel;
            this.microsoftAccount = microsoftAccount;
            this.hasMinecraftSession = hasMinecraftSession;
        }

        public static Account offlineAccount(@NonNull String username) {
            return new Account(
                    "offline-" + UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes()),
                    username,
                    null, null, null, null,
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

        public boolean isOfflineAccount() {
            return !microsoftAccount;
        }

        public boolean hasMinecraftSession() {
            return hasMinecraftSession;
        }

        public boolean hasOfflineSkin() {
            return isOfflineAccount() && offlineSkinPath != null && !offlineSkinPath.isEmpty();
        }

        @NonNull
        JSONObject toJson() throws Exception {
            JSONObject obj = new JSONObject();
            obj.put("accountId", accountId);
            obj.put("username", username);
            if (minecraftAccessToken != null) obj.put("minecraftAccessToken", minecraftAccessToken);
            if (skinUrl != null) obj.put("skinUrl", skinUrl);
            if (offlineSkinPath != null) obj.put("offlineSkinPath", offlineSkinPath);
            if (offlineSkinModel != null) obj.put("offlineSkinModel", offlineSkinModel);
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
                String sUrl      = obj.optString("skinUrl", null);
                String skinPath  = obj.optString("offlineSkinPath", null);
                String skinModel = obj.optString("offlineSkinModel", null);
                boolean microsoft = obj.optBoolean("microsoftAccount", false);
                boolean session   = obj.optBoolean("hasMinecraftSession", false);
                return new Account(accountId, username, token, sUrl, skinPath, skinModel, microsoft, session);
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    // ── Store ─────────────────────────────────────────────────────────────────

    private final Context context;
    private final SharedPreferences prefs;

    public AccountStore(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Always returns true — offline accounts are available without Microsoft login.
     * This bypasses the Microsoft-login gate in LauncherSettingsActivity.
     */
    public boolean hasMicrosoftLoginCompletedOnce() {
        return true;
    }

    /**
     * Always returns false — Microsoft accounts are not supported in this offline build.
     */
    public boolean hasStoredMicrosoftAccount() {
        return false;
    }

    /**
     * Load the currently active account.
     * Returns the first offline account if no explicit selection is persisted,
     * or creates a default "Player" account if none exist.
     */
    @Nullable
    public Account load() {
        String activeId = prefs.getString(KEY_ACTIVE_ACCOUNT_ID, null);

        ArrayList<Account> offlineAccounts = listOfflineAccounts();
        if (activeId != null) {
            for (Account a : offlineAccounts) {
                if (activeId.equals(a.accountId)) return a;
            }
        }

        if (!offlineAccounts.isEmpty()) return offlineAccounts.get(0);

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

    /** Set the active account by its ID. No-op if account not found. */
    public void activateOfflineAccount(@NonNull String accountId) {
        for (Account a : listOfflineAccounts()) {
            if (a.accountId.equals(accountId)) {
                setActiveAccount(a);
                return;
            }
        }
    }

    /**
     * Not supported in the offline build. Always throws.
     */
    public void useLastMicrosoftAccount() {
        throw new IllegalStateException("Microsoft accounts are not available in this build.");
    }

    // ── Offline accounts ──────────────────────────────────────────────────────

    @NonNull
    public ArrayList<Account> listOfflineAccounts() {
        ArrayList<Account> list = new ArrayList<>();
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
        ArrayList<Account> accounts = listOfflineAccounts();
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
        // Delete any associated skin file first
        for (Account a : listOfflineAccounts()) {
            if (a.accountId.equals(accountId) && a.offlineSkinPath != null) {
                new File(a.offlineSkinPath).delete();
            }
        }

        ArrayList<Account> accounts = listOfflineAccounts();
        accounts.removeIf(a -> a.accountId.equals(accountId));
        persistOfflineAccounts(accounts);

        String activeId = prefs.getString(KEY_ACTIVE_ACCOUNT_ID, null);
        if (accountId.equals(activeId)) {
            prefs.edit().remove(KEY_ACTIVE_ACCOUNT_ID).apply();
        }
    }

    /**
     * Creates or updates an offline account with optional skin.
     *
     * @param existingId  accountId of the account to update, or null to create a new one
     * @param name        player username (3-16 alphanumeric/underscore chars)
     * @param skinUri     URI to a PNG skin file, or null to leave/clear unchanged
     * @param clearSkin   if true, removes the existing skin (skinUri is ignored when clearing)
     * @return the created or updated Account (also set as the active account)
     */
    @NonNull
    public Account saveOrUpdateOfflineAccount(
            @Nullable String existingId,
            @NonNull String name,
            @Nullable Uri skinUri,
            boolean clearSkin
    ) throws Exception {
        String id = existingId != null
                ? existingId
                : "offline-" + UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes());

        Account existing = null;
        for (Account a : listOfflineAccounts()) {
            if (a.accountId.equals(id)) {
                existing = a;
                break;
            }
        }

        String skinPath  = existing != null ? existing.offlineSkinPath  : null;
        String skinModel = existing != null ? existing.offlineSkinModel : null;

        if (clearSkin) {
            if (skinPath != null) new File(skinPath).delete();
            skinPath  = null;
            skinModel = null;
        }

        if (skinUri != null) {
            File skinDir = new File(context.getFilesDir(), "skins");
            //noinspection ResultOfMethodCallIgnored
            skinDir.mkdirs();
            File skinFile = new File(skinDir, "offline_" + id + ".png");

            try (InputStream in = context.getContentResolver().openInputStream(skinUri);
                 FileOutputStream out = new FileOutputStream(skinFile)) {
                if (in == null) throw new IllegalStateException("Cannot read the selected skin file.");
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            }

            skinPath  = skinFile.getAbsolutePath();
            skinModel = detectSkinModel(skinFile);
        }

        Account account = new Account(id, name, null, null, skinPath, skinModel, false, false);
        saveOfflineAccount(account);
        setActiveAccount(account);
        return account;
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

    @NonNull
    private static String detectSkinModel(@NonNull File skinFile) {
        try {
            android.graphics.Bitmap bmp =
                    android.graphics.BitmapFactory.decodeFile(skinFile.getAbsolutePath());
            if (bmp == null || bmp.getWidth() < 64 || bmp.getHeight() < 64) {
                if (bmp != null) bmp.recycle();
                return "classic";
            }
            int pixel = bmp.getPixel(50, 16);
            bmp.recycle();
            int alpha = (pixel >> 24) & 0xFF;
            return alpha == 0 ? "slim" : "classic";
        } catch (Throwable ignored) {
            return "classic";
        }
    }
}
