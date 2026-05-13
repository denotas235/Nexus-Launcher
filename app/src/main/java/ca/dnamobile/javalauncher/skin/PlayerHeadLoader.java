package ca.dnamobile.javalauncher.skin;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;

import ca.dnamobile.javalauncher.data.AccountStore;

/**
 * Utility for loading and cropping a player's head from a skin texture.
 *
 * Skin layout (64×64 or 64×32):
 *  - Face layer  : x=8,  y=8,  w=8, h=8
 *  - Hat layer   : x=40, y=8,  w=8, h=8
 *
 * The rendered head is a 72×72 composite (hat drawn over face).
 */
public final class PlayerHeadLoader {

    private static final int FACE_X = 8,  FACE_Y = 8;
    private static final int HAT_X  = 40, HAT_Y  = 8;
    private static final int HEAD_SIZE = 8;

    private PlayerHeadLoader() {}

    /**
     * Convenience overload that accepts a {@link android.content.Context} as the first
     * argument (ignored) so call-sites that pass a Context compile without changes.
     */
    public static void loadInto(
            @NonNull android.content.Context context,
            @NonNull ImageView imageView,
            @Nullable AccountStore.Account account,
            @Nullable File skinFile
    ) {
        loadInto(imageView, account, skinFile);
    }

    /**
     * Loads the player head into {@code imageView}.
     * Falls back to the account username initial if no skin is available.
     */
    public static void loadInto(
            @NonNull ImageView imageView,
            @Nullable AccountStore.Account account,
            @Nullable File skinFile
    ) {
        Bitmap head = skinFile != null ? loadHeadFromSkinFile(skinFile) : null;
        if (head != null) {
            imageView.setImageBitmap(head);
        } else {
            imageView.setImageResource(ca.dnamobile.javalauncher.R.drawable.ic_player_head_placeholder);
        }
    }

    /**
     * Decodes the skin file and extracts the 8×8 face region, compositing the
     * hat layer on top. Returns a scaled 64×64 bitmap, or null on failure.
     */
    @Nullable
    public static Bitmap loadHeadFromSkinFile(@NonNull File skinFile) {
        if (!skinFile.isFile()) return null;
        try {
            Bitmap skin = BitmapFactory.decodeFile(skinFile.getAbsolutePath());
            if (skin == null || skin.getWidth() < 64 || skin.getHeight() < 32) {
                if (skin != null) skin.recycle();
                return null;
            }

            // Extract face
            Bitmap face = Bitmap.createBitmap(skin, FACE_X, FACE_Y, HEAD_SIZE, HEAD_SIZE);

            // Extract hat (overlay) — only available on 64×64 skins
            Bitmap result = Bitmap.createScaledBitmap(face, 64, 64, false);
            face.recycle();

            if (skin.getHeight() >= 64) {
                Bitmap hat = Bitmap.createBitmap(skin, HAT_X, HAT_Y, HEAD_SIZE, HEAD_SIZE);
                Bitmap hatScaled = Bitmap.createScaledBitmap(hat, 64, 64, false);
                hat.recycle();

                Bitmap composite = result.copy(Bitmap.Config.ARGB_8888, true);
                Canvas canvas = new Canvas(composite);
                canvas.drawBitmap(hatScaled, 0, 0, null);
                hatScaled.recycle();
                result.recycle();
                result = composite;
            }

            skin.recycle();
            return result;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
