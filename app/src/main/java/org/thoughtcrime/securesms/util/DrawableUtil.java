package org.thoughtcrime.securesms.util;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.graphics.drawable.DrawableCompat;

public final class DrawableUtil {

  private DrawableUtil() {}

  public static @NonNull Bitmap toBitmap(@NonNull Drawable drawable, int width, int height) {
    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap);

    drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
    drawable.draw(canvas);

    return bitmap;
  }

  public static @NonNull Bitmap wrapBitmapForShortcutInfo(@NonNull Bitmap toWrap) {
    Bitmap bitmap = Bitmap.createBitmap(AdaptiveBitmapMetrics.getOuterWidth(), AdaptiveBitmapMetrics.getOuterWidth(), Bitmap.Config.ARGB_8888);
    Bitmap scaled = Bitmap.createScaledBitmap(toWrap, AdaptiveBitmapMetrics.getInnerWidth(), AdaptiveBitmapMetrics.getInnerWidth(), true);

    Canvas canvas = new Canvas(bitmap);
    canvas.drawBitmap(scaled, AdaptiveBitmapMetrics.getPadding(), AdaptiveBitmapMetrics.getPadding(), null);

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
