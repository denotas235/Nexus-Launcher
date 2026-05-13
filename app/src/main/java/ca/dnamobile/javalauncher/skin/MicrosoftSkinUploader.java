package ca.dnamobile.javalauncher.skin;

import androidx.annotation.NonNull;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Uploads a skin PNG to the Minecraft profile service on behalf of a Microsoft account.
 *
 * This stub is only reachable when a Microsoft account with a valid session is active.
 * In the standard offline build it is never called.
 */
public final class MicrosoftSkinUploader {

    private static final String SKIN_UPLOAD_URL =
            "https://api.minecraftservices.com/minecraft/profile/skins";
    private static final String BOUNDARY = "----DroidBridgeSkinBoundary7MA4YWxkTrZu0gW";

    private MicrosoftSkinUploader() {}

    /**
     * Uploads {@code skinFile} to the Minecraft profile service.
     *
     * @param accessToken   Minecraft access token for the authenticated session.
     * @param skinFile      A valid 64×64 PNG skin texture.
     * @param model         Skin model variant (classic or slim).
     * @throws Exception    on network or HTTP errors.
     */
    public static void uploadSkin(
            @NonNull String accessToken,
            @NonNull File skinFile,
            @NonNull SkinModelType model
    ) throws Exception {
        String modelVariant = model == SkinModelType.SLIM ? "slim" : "classic";

        URL url = new URL(SKIN_UPLOAD_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + BOUNDARY);
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(15_000);

        try (DataOutputStream out = new DataOutputStream(conn.getOutputStream())) {
            // variant field
            out.writeBytes("--" + BOUNDARY + "\r\n");
            out.writeBytes("Content-Disposition: form-data; name=\"variant\"\r\n\r\n");
            out.writeBytes(modelVariant + "\r\n");

            // skin file
            out.writeBytes("--" + BOUNDARY + "\r\n");
            out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"skin.png\"\r\n");
            out.writeBytes("Content-Type: image/png\r\n\r\n");
            try (InputStream fis = new FileInputStream(skinFile)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = fis.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
            out.writeBytes("\r\n--" + BOUNDARY + "--\r\n");
        }

        int code = conn.getResponseCode();
        conn.disconnect();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("Skin upload failed. HTTP " + code);
        }
    }
}
