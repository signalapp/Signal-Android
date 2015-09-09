package org.thoughtcrime.redphone.util;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;

public class BitmapUtil {

  public static Bitmap getCircleCroppedBitmap(Bitmap bitmap) {
    if (bitmap == null) return null;
    final int srcSize = Math.min(bitmap.getWidth(), bitmap.getHeight());
    return getScaledCircleCroppedBitmap(bitmap, srcSize);
  }

  public static Bitmap getScaledCircleCroppedBitmap(Bitmap bitmap, int destSize) {
    if (bitmap == null) return null;
    Bitmap output = Bitmap.createBitmap(destSize, destSize, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(output);

    final int srcSize = Math.min(bitmap.getWidth(), bitmap.getHeight());
    final int srcX = (bitmap.getWidth() - srcSize) / 2;
    final int srcY = (bitmap.getHeight() - srcSize) / 2;
    final Rect srcRect = new Rect(srcX, srcY, srcX + srcSize, srcY + srcSize);
    final Rect destRect = new Rect(0, 0, destSize, destSize);
    final int color = 0xff424242;
    final Paint paint = new Paint();

    paint.setAntiAlias(true);
    canvas.drawARGB(0, 0, 0, 0);
    paint.setColor(color);
    canvas.drawCircle(destSize / 2, destSize / 2, destSize / 2, paint);
    paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
    canvas.drawBitmap(bitmap, srcRect, destRect, paint);
    return output;
  }

}
