package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.net.Uri;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public class BitmapUtil {

  private static final int MAX_COMPRESSION_QUALITY  = 95;
  private static final int MIN_COMPRESSION_QUALITY  = 50;
  private static final int MAX_COMPRESSION_ATTEMPTS = 4;

  public static byte[] createScaledBytes(Context context, Uri uri, int maxWidth,
                                         int maxHeight, int maxSize)
      throws IOException, BitmapDecodingException
  {
    InputStream measure = context.getContentResolver().openInputStream(uri);
    InputStream data    = context.getContentResolver().openInputStream(uri);
    Bitmap bitmap       = createScaledBitmap(measure, data, maxWidth, maxHeight);
    int quality         = MAX_COMPRESSION_QUALITY;
    int attempts        = 0;

    ByteArrayOutputStream baos;

    do {
      baos = new ByteArrayOutputStream();
      bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);

      quality = Math.max((quality * maxSize) / baos.size(), MIN_COMPRESSION_QUALITY);
    } while (baos.size() > maxSize && attempts++ < MAX_COMPRESSION_ATTEMPTS);

    bitmap.recycle();

    if (baos.size() <= maxSize) return baos.toByteArray();
    else                        throw new IOException("Unable to scale image below: " + baos.size());
  }

  public static Bitmap createScaledBitmap(InputStream measure, InputStream data,
                                          int maxWidth, int maxHeight)
      throws BitmapDecodingException
  {
    BitmapFactory.Options options = getImageDimensions(measure);
    int imageWidth                = options.outWidth;
    int imageHeight               = options.outHeight;

    int scaler = 1;

    while ((imageWidth / scaler > maxWidth) && (imageHeight / scaler > maxHeight))
      scaler *= 2;

    if (scaler > 1)
      scaler /= 2;

    options.inSampleSize       = scaler;
    options.inJustDecodeBounds = false;

    Bitmap roughThumbnail  = BitmapFactory.decodeStream(new BufferedInputStream(data), null, options);

    if (roughThumbnail == null) {
      throw new BitmapDecodingException("Decoded stream was null.");
    }

    if (roughThumbnail.getWidth() > maxWidth || roughThumbnail.getHeight() > maxHeight) {
      float aspectWidth, aspectHeight;

      if (imageWidth == 0 || imageHeight == 0) {
        aspectWidth = maxWidth;
        aspectHeight = maxHeight;
      } else if (options.outWidth >= options.outHeight) {
        aspectWidth = maxWidth;
        aspectHeight = (aspectWidth / options.outWidth) * options.outHeight;
      } else {
        aspectHeight = maxHeight;
        aspectWidth = (aspectHeight / options.outHeight) * options.outWidth;
      }

      Log.w("BitmapUtil", "Scaling to max width and height: " + aspectWidth + "," + aspectHeight);
      Bitmap scaledThumbnail = Bitmap.createScaledBitmap(roughThumbnail, (int)aspectWidth, (int)aspectHeight, true);
      roughThumbnail.recycle();
      return scaledThumbnail;
    } else {
      return roughThumbnail;
    }
  }

  private static BitmapFactory.Options getImageDimensions(InputStream inputStream) {
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inJustDecodeBounds    = true;
    BitmapFactory.decodeStream(inputStream, null, options);

    return options;
  }

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

  public static byte[] toByteArray(Bitmap bitmap) {
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
    return stream.toByteArray();
  }
}
