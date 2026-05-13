package ca.dnamobile.javalauncher.skin;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;

/**
 * Manages custom offline player skins stored on the device.
 */
public final class CustomSkinStore {

    private static final int EXPECTED_WIDTH_64  = 64;
    private static final int EXPECTED_HEIGHT_64 = 64;
    private static final int EXPECTED_HEIGHT_32 = 32;
    private static final String SKIN_FILENAME = "offline_skin.png";

    private CustomSkinStore() {}

    /**
     * Returns the file used to persist the custom offline skin, or null if no
     * custom skin has been set.
     */
    @Nullable
    public static File getSkinFile(@NonNull Context context) {
        File f = skinFile(context);
        return f.isFile() ? f : null;
    }

    /** Validates that the file is a valid 64×32 or 64×64 PNG skin texture. */
    public static boolean isSkinValid(@NonNull File file) {
        if (!file.isFile() || file.length() < 8) return false;
        try {
            android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
            if (opts.outWidth != EXPECTED_WIDTH_64) return false;
            return opts.outHeight == EXPECTED_HEIGHT_64 || opts.outHeight == EXPECTED_HEIGHT_32;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Detects whether the skin uses the slim (Alex) or classic (Steve) arm model.
     * Detection uses the pixel at (50, 16) — if it is fully transparent the skin
     * is slim (the extra 3-px pixels in the classic model are opaque there).
     */
    @NonNull
    public static SkinModelType getSkinModel(@NonNull File file) {
        try {
            android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath());
            if (bmp == null) return SkinModelType.CLASSIC;
            if (bmp.getWidth() < 64 || bmp.getHeight() < 64) {
                bmp.recycle();
                return SkinModelType.CLASSIC;
            }
            int pixel = bmp.getPixel(50, 16);
            bmp.recycle();
            // Transparent pixel at (50,16) indicates slim model
            int alpha = (pixel >> 24) & 0xFF;
            return alpha == 0 ? SkinModelType.SLIM : SkinModelType.CLASSIC;
        } catch (Throwable ignored) {
            return SkinModelType.CLASSIC;
        }
    }

    /** Saves the given skin file as the active custom offline skin. */
    public static void saveSkin(@NonNull Context context, @NonNull File sourceFile) throws Exception {
        File dest = skinFile(context);
        //noinspection ResultOfMethodCallIgnored
        dest.getParentFile().mkdirs();
        java.nio.file.Files.copy(
                sourceFile.toPath(),
                dest.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
        );
    }

    /** Deletes the custom offline skin. */
    public static void clearSkin(@NonNull Context context) {
        //noinspection ResultOfMethodCallIgnored
        skinFile(context).delete();
    }

    @NonNull
    private static File skinFile(@NonNull Context context) {
        return new File(context.getFilesDir(), "skins/" + SKIN_FILENAME);
    }
}
