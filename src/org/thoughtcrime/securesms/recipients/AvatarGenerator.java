package org.thoughtcrime.securesms.recipients;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.contacts.ContactPhotoFactory;
import org.thoughtcrime.securesms.util.BitmapUtil;

/**
 * Utility class to generate avatars for contacts who don't have a contact
 * picture set.
 *
 * @author Lukas Barth
 */
public class AvatarGenerator {

  public static Bitmap generateFor(Context context, Recipient recipient, int setColor) {
    if ((recipient == null) || (recipient.getName() == null)) {
      return BitmapUtil.getCircleBitmap(ContactPhotoFactory.getDefaultContactPhoto(context));
    }

    final int    size            = ContactPhotoFactory.getDefaultContactPhoto(context).getHeight();
    final Bitmap output          = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
    final Canvas canvas          = new Canvas(output);
    final int    color           = (setColor != -1) ? setColor : getColorForRecipient(recipient, context);
    final Paint  paint           = new Paint();
    final int    innerRectOffset = (int) Math.ceil((size - Math.sqrt(2) * (size / 2)) / 2);
    final Rect   innerRect       = new Rect(innerRectOffset, innerRectOffset,
                                            size - innerRectOffset, size - innerRectOffset);

    paint.setAntiAlias(true);
    paint.setColor(color);
    canvas.drawCircle(size / 2, size / 2, size / 2, paint);

    paint.setColor(Color.WHITE);
    Typeface robotoLightTypeface = Typeface.createFromAsset(context.getAssets(), "fonts/Roboto-Light.ttf");
    paint.setTypeface(robotoLightTypeface);
    setFontSize(innerRect, paint);

    paint.setTextAlign(Paint.Align.CENTER);

    int initialIndex = 0;
    char[] contactName = recipient.getName().toCharArray();

    if (contactName.length == 0) {
      contactName = new char[]{'?'};
      initialIndex = 0;
    } else {
      while ((! Character.isLetter(contactName[initialIndex]))) {
        initialIndex ++;

        if (initialIndex >= contactName.length) {
          contactName[0] = '?';
          initialIndex = 0;
          break;
        }
      }
    }

    Rect textBounds = new Rect();
    paint.getTextBounds(contactName, initialIndex, 1, textBounds);

    int bottomOffset = (innerRect.height() - textBounds.height()) / 2;

    canvas.drawText(Character.toString(contactName[initialIndex]),
                    innerRect.centerX(), innerRect.bottom - bottomOffset, paint);

    return output;
  }


  private static int getColorForRecipient(Recipient recipient, Context context) {
    if ((recipient == null) || (recipient.getName() == null)) {
      return Color.WHITE;
    }

    long       nameHash   = recipient.getName().hashCode();
    Resources  res        = context.getResources();
    TypedArray colorArray = res.obtainTypedArray(R.array.avatar_colors);
    int        index      = Math.abs((int) (nameHash % colorArray.length()));
    int        color      = colorArray.getColor(index, Color.BLACK);

    colorArray.recycle();

    return color;
  }

  private static int setFontSize(Rect textRect, Paint paint) {
    boolean overflow = false;
    int currentSize = 0;

    while (!overflow) {
      currentSize++;
      paint.setTextSize(currentSize);

      Paint.FontMetricsInt fontMetrics = paint.getFontMetricsInt();
      int textHeight = fontMetrics.descent - fontMetrics.ascent;

      if (textHeight > textRect.height()) {
        overflow = true;
      }
    }

    currentSize--;

    currentSize *= 1.2;

    paint.setTextSize(currentSize);

    return currentSize;
  }
}
