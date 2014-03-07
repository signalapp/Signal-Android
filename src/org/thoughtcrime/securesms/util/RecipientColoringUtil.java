package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.recipients.Recipient;

/**
 * Utility class to map contacts to colors that identify them in group chats
 *
 * @author Lukas Barth
 */
public class RecipientColoringUtil {
  public static int getColorForRecipient(Recipient recipient, Context context) {
    long nameHash = recipient.getName().hashCode();

    return Color.HSVToColor(80,
            new float[]{
                    (nameHash % 3600) / 10.0f,  // Hue
                    0.75f,                      // Saturation
                    0.75f,                      // Value
            });
  }

  public static Bitmap colorBitmapForRecipient(Bitmap bitmap, Recipient recipient, Context context) {
    Bitmap coloredBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), bitmap.getConfig());
    Canvas canvas = new Canvas(coloredBitmap);
    Paint paint = new Paint();

    ColorFilter colorFilter = new PorterDuffColorFilter(RecipientColoringUtil.getColorForRecipient(recipient, context),
            PorterDuff.Mode.DST_OVER);
    paint.setColorFilter(colorFilter);

    canvas.drawBitmap(bitmap, new Matrix(), paint);

    return coloredBitmap;
  }
}
