package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

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
      throws IOException
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

    Bitmap roughThumbnail  = BitmapFactory.decodeStream(data, null, options);

    if (imageWidth > maxWidth || imageHeight > maxHeight) {
      Log.w("BitmapUtil", "Scaling to max width and height: " + maxWidth + "," + maxHeight);
      Bitmap scaledThumbnail = Bitmap.createScaledBitmap(roughThumbnail, maxWidth, maxHeight, true);
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


}
