package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.net.Uri;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import com.android.gallery3d.data.Exif;

public class BitmapUtil {
  private static final String TAG = BitmapUtil.class.getSimpleName();

  private static final int MAX_COMPRESSION_QUALITY  = 95;
  private static final int MIN_COMPRESSION_QUALITY  = 50;
  private static final int MAX_COMPRESSION_ATTEMPTS = 4;

  public static byte[] createScaledBytes(Context context, Uri uri, int maxWidth,
                                         int maxHeight, int maxSize)
      throws IOException, BitmapDecodingException
  {
    Bitmap bitmap;
    try {
      bitmap = createScaledBitmap(context, uri, maxWidth, maxHeight, false);
    } catch(OutOfMemoryError oome) {
      Log.w(TAG, "OutOfMemoryError when scaling precisely, doing rough scale to save memory instead");
      bitmap = createScaledBitmap(context, uri, maxWidth, maxHeight, true);
    }
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

  public static Bitmap createScaledBitmap(InputStream measure, InputStream data, float scale)
      throws BitmapDecodingException
  {
    final BitmapFactory.Options options = getImageDimensions(measure);
    final int outWidth = (int)(options.outWidth * scale);
    final int outHeight = (int)(options.outHeight * scale);
    Log.w(TAG, "creating scaled bitmap with scale " + scale + " => " + outWidth + "x" + outHeight);
    return createScaledBitmap(data, outWidth, outHeight, options, false);
  }

  public static Bitmap createScaledBitmap(InputStream measure, InputStream data,
                                          int maxWidth, int maxHeight)
      throws BitmapDecodingException
  {
    return createScaledBitmap(measure, data, maxWidth, maxHeight, false);
  }

  public static Bitmap createScaledBitmap(InputStream measure, InputStream data,
                                         int maxWidth, int maxHeight, boolean constrainedMemory)
      throws BitmapDecodingException
  {
    final BitmapFactory.Options options = getImageDimensions(measure);
    return createScaledBitmap(data, maxWidth, maxHeight, options, constrainedMemory);
  }

  private static Bitmap createScaledBitmap(Context context, Uri uri, int maxWidth, int maxHeight,
                                          boolean constrainedMemory) throws IOException, BitmapDecodingException {
    return createScaledBitmap(context.getContentResolver().openInputStream(uri),
                              context.getContentResolver().openInputStream(uri),
                              context.getContentResolver().openInputStream(uri),
                              maxWidth, maxHeight, constrainedMemory);
  }

  public static Bitmap createScaledBitmap(InputStream measure, InputStream orientationStream, InputStream data,
                                          int maxWidth, int maxHeight, boolean constrainedMemory)
      throws BitmapDecodingException
  {
    Bitmap bitmap         = createScaledBitmap(measure, data, maxWidth, maxHeight, constrainedMemory);
    final int orientation = Exif.getOrientation(orientationStream);

    if (orientation != 0) {
      return rotateBitmap(bitmap, orientation);
    } else {
      return bitmap;
    }
  }

  private static Bitmap rotateBitmap(Bitmap bitmap, int angle) {
    Matrix matrix = new Matrix();
    matrix.postRotate(angle);
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
  }

  private static Bitmap createScaledBitmap(InputStream data, int maxWidth, int maxHeight,
                                           BitmapFactory.Options options, boolean constrainedMemory)
      throws BitmapDecodingException
  {
    final int imageWidth  = options.outWidth;
    final int imageHeight = options.outHeight;

    int scaler = 1;
    int scaleFactor = (constrainedMemory ? 1 : 2);
    while ((imageWidth / scaler / scaleFactor >= maxWidth) && (imageHeight / scaler / scaleFactor >= maxHeight)) {
      scaler *= 2;
    }

    options.inSampleSize       = scaler;
    options.inJustDecodeBounds = false;

    BufferedInputStream is = new BufferedInputStream(data);
    Bitmap roughThumbnail  = BitmapFactory.decodeStream(is, null, options);
    try {
      is.close();
    } catch (IOException ioe) {
      Log.w(TAG, "IOException thrown when closing an images InputStream", ioe);
    }
    Log.w(TAG, "rough scale " + (imageWidth) + "x" + (imageHeight) +
               " => " + (options.outWidth) + "x" + (options.outHeight));
    if (roughThumbnail == null) {
      throw new BitmapDecodingException("Decoded stream was null.");
    }
    if (constrainedMemory) {
      return roughThumbnail;
    }

    if (options.outWidth > maxWidth || options.outHeight > maxHeight) {
      final float aspectWidth, aspectHeight;

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

      Log.w(TAG, "fine scale  " + options.outWidth + "x" + options.outHeight +
                 " => " + aspectWidth + "x" + aspectHeight);
      Bitmap scaledThumbnail = null;
      try {
        scaledThumbnail = Bitmap.createScaledBitmap(roughThumbnail, (int) aspectWidth, (int) aspectHeight, true);
      } finally {
        if (roughThumbnail != scaledThumbnail) roughThumbnail.recycle();
      }
      return scaledThumbnail;
    } else {
      return roughThumbnail;
    }
  }

  private static BitmapFactory.Options getImageDimensions(InputStream inputStream) {
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inJustDecodeBounds    = true;
    BufferedInputStream fis       = new BufferedInputStream(inputStream);
    BitmapFactory.decodeStream(fis, null, options);
    try {
      fis.close();
    } catch (IOException ioe) {
      Log.w(TAG, "failed to close the InputStream after reading image dimensions");
    }
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
