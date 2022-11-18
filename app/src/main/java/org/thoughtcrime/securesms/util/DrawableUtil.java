package org.thoughtcrime.securesms.util;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.graphics.drawable.DrawableCompat;

public final class DrawableUtil {

  private static final int SHORTCUT_INFO_BITMAP_SIZE  = ViewUtil.dpToPx(108);
  public  static final int SHORTCUT_INFO_WRAPPED_SIZE = ViewUtil.dpToPx(72);
  private static final int SHORTCUT_INFO_PADDING      = (SHORTCUT_INFO_BITMAP_SIZE - SHORTCUT_INFO_WRAPPED_SIZE) / 2;

  private DrawableUtil() {}

  public static @NonNull Bitmap toBitmap(@NonNull Drawable drawable, int width, int height) {
    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap);

    drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
    drawable.draw(canvas);

    return bitmap;
  }

  public static @NonNull Bitmap wrapBitmapForShortcutInfo(@NonNull Bitmap toWrap) {
    Bitmap bitmap = Bitmap.createBitmap(SHORTCUT_INFO_BITMAP_SIZE, SHORTCUT_INFO_BITMAP_SIZE, Bitmap.Config.ARGB_8888);
    Bitmap scaled = Bitmap.createScaledBitmap(toWrap, SHORTCUT_INFO_WRAPPED_SIZE, SHORTCUT_INFO_WRAPPED_SIZE, true);

    Canvas canvas = new Canvas(bitmap);
    canvas.drawBitmap(scaled, SHORTCUT_INFO_PADDING, SHORTCUT_INFO_PADDING, null);

    return bitmap;
  }

  /**
   * Returns a new {@link Drawable} that safely wraps and tints the provided drawable.
   */
  public static @NonNull Drawable tint(@NonNull Drawable drawable, @ColorInt int tint) {
    Drawable tinted = DrawableCompat.wrap(drawable).mutate();
    DrawableCompat.setTint(tinted, tint);
    return tinted;
  }
}
