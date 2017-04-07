package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.util.Pair;

import com.bumptech.glide.Glide;
import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.Resource;
import com.bumptech.glide.load.resource.bitmap.BitmapResource;
import com.bumptech.glide.load.resource.bitmap.Downsampler;
import com.bumptech.glide.load.resource.bitmap.FitCenter;
import com.bumptech.glide.Util;

import org.thoughtcrime.securesms.mms.MediaConstraints;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;

public class BitmapUtil {

  private static final String TAG = BitmapUtil.class.getSimpleName();

  private static final int MAX_COMPRESSION_QUALITY          = 90;
  private static final int MIN_COMPRESSION_QUALITY          = 45;
  private static final int MAX_COMPRESSION_ATTEMPTS         = 5;
  private static final int MIN_COMPRESSION_QUALITY_DECREASE = 5;

  public static <T> byte[] createScaledBytes(Context context, T model, MediaConstraints constraints)
      throws BitmapDecodingException
  {
    int    quality  = MAX_COMPRESSION_QUALITY;
    int    attempts = 0;
    byte[] bytes;

    Bitmap scaledBitmap =  Downsampler.AT_MOST.decode(getInputStreamForModel(context, model),
                                                      Glide.get(context).getBitmapPool(),
                                                      constraints.getImageMaxWidth(context),
                                                      constraints.getImageMaxHeight(context),
                                                      DecodeFormat.PREFER_RGB_565);

    if (scaledBitmap == null) {
      throw new BitmapDecodingException("Unable to decode image");
    }
    
    try {
      do {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        scaledBitmap.compress(CompressFormat.JPEG, quality, baos);
        bytes = baos.toByteArray();

        Log.w(TAG, "iteration with quality " + quality + " size " + (bytes.length / 1024) + "kb");
        if (quality == MIN_COMPRESSION_QUALITY) break;

        int nextQuality = (int)Math.floor(quality * Math.sqrt((double)constraints.getImageMaxSize() / bytes.length));
        if (quality - nextQuality < MIN_COMPRESSION_QUALITY_DECREASE) {
          nextQuality = quality - MIN_COMPRESSION_QUALITY_DECREASE;
        }
        quality = Math.max(nextQuality, MIN_COMPRESSION_QUALITY);
      }
      while (bytes.length > constraints.getImageMaxSize() && attempts++ < MAX_COMPRESSION_ATTEMPTS);
      if (bytes.length > constraints.getImageMaxSize()) {
        throw new BitmapDecodingException("Unable to scale image below: " + bytes.length);
      }
      Log.w(TAG, "createScaledBytes(" + model.toString() + ") -> quality " + Math.min(quality, MAX_COMPRESSION_QUALITY) + ", " + attempts + " attempt(s)");
      return bytes;
    } finally {
      if (scaledBitmap != null) scaledBitmap.recycle();
    }
  }

  public static <T> Bitmap createScaledBitmap(Context context, T model, int maxWidth, int maxHeight)
      throws BitmapDecodingException
  {
    final Pair<Integer, Integer> dimensions = getDimensions(getInputStreamForModel(context, model));
    final Pair<Integer, Integer> clamped    = clampDimensions(dimensions.first, dimensions.second,
                                                              maxWidth, maxHeight);
    return createScaledBitmapInto(context, model, clamped.first, clamped.second);
  }

  private static <T> InputStream getInputStreamForModel(Context context, T model)
      throws BitmapDecodingException
  {
    try {
      return Glide.buildStreamModelLoader(model, context)
                  .getResourceFetcher(model, -1, -1)
                  .loadData(Priority.NORMAL);
    } catch (Exception e) {
      throw new BitmapDecodingException(e);
    }
  }

  private static <T> Bitmap createScaledBitmapInto(Context context, T model, int width, int height)
      throws BitmapDecodingException
  {
    final Bitmap rough = Downsampler.AT_LEAST.decode(getInputStreamForModel(context, model),
                                                     Glide.get(context).getBitmapPool(),
                                                     width, height,
                                                     DecodeFormat.PREFER_RGB_565);

    final Resource<Bitmap> resource = BitmapResource.obtain(rough, Glide.get(context).getBitmapPool());
    final Resource<Bitmap> result   = Util.isValidDimensions(width,height)?
                            new FitCenter(context).transform(resource, width, height):
                      BitmapFactory.decodeResource(context.getResources(),R.drawable.ic_error_red_24dp);

    if (result == null) {
      throw new BitmapDecodingException("unable to transform Bitmap");
    }
    return result.get();
  }

  public static <T> Bitmap createScaledBitmap(Context context, T model, float scale)
      throws BitmapDecodingException
  {
    Pair<Integer, Integer> dimens = getDimensions(getInputStreamForModel(context, model));
    return createScaledBitmapInto(context, model,
                                  (int)(dimens.first * scale), (int)(dimens.second * scale));
  }

  private static BitmapFactory.Options getImageDimensions(InputStream inputStream)
      throws BitmapDecodingException
  {
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inJustDecodeBounds    = true;
    BufferedInputStream fis       = new BufferedInputStream(inputStream);
    BitmapFactory.decodeStream(fis, null, options);
    try {
      fis.close();
    } catch (IOException ioe) {
      Log.w(TAG, "failed to close the InputStream after reading image dimensions");
    }

    if (options.outWidth == -1 || options.outHeight == -1) {
      throw new BitmapDecodingException("Failed to decode image dimensions: " + options.outWidth + ", " + options.outHeight);
    }

    return options;
  }

  public static Pair<Integer, Integer> getDimensions(InputStream inputStream) throws BitmapDecodingException {
    BitmapFactory.Options options = getImageDimensions(inputStream);
    return new Pair<>(options.outWidth, options.outHeight);
  }

  public static InputStream toCompressedJpeg(Bitmap bitmap) {
    ByteArrayOutputStream thumbnailBytes = new ByteArrayOutputStream();
    bitmap.compress(CompressFormat.JPEG, 85, thumbnailBytes);
    return new ByteArrayInputStream(thumbnailBytes.toByteArray());
  }

  public static @Nullable byte[] toByteArray(@Nullable Bitmap bitmap) {
    if (bitmap == null) return null;
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
    return stream.toByteArray();
  }

  public static @Nullable Bitmap fromByteArray(@Nullable byte[] bytes) {
    if (bytes == null) return null;
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
  }

  public static byte[] createFromNV21(@NonNull final byte[] data,
                                      final int width,
                                      final int height,
                                      int rotation,
                                      final Rect croppingRect,
                                      final boolean flipHorizontal)
      throws IOException
  {
    byte[] rotated = rotateNV21(data, width, height, rotation, flipHorizontal);
    final int rotatedWidth  = rotation % 180 > 0 ? height : width;
    final int rotatedHeight = rotation % 180 > 0 ? width  : height;
    YuvImage previewImage = new YuvImage(rotated, ImageFormat.NV21,
                                         rotatedWidth, rotatedHeight, null);

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    previewImage.compressToJpeg(croppingRect, 80, outputStream);
    byte[] bytes = outputStream.toByteArray();
    outputStream.close();
    return bytes;
  }

  /*
   * NV21 a.k.a. YUV420sp
   * YUV 4:2:0 planar image, with 8 bit Y samples, followed by interleaved V/U plane with 8bit 2x2
   * subsampled chroma samples.
   *
   * http://www.fourcc.org/yuv.php#NV21
   */
  public static byte[] rotateNV21(@NonNull final byte[] yuv,
                                  final int width,
                                  final int height,
                                  final int rotation,
                                  final boolean flipHorizontal)
      throws IOException
  {
    if (rotation == 0) return yuv;
    if (rotation % 90 != 0 || rotation < 0 || rotation > 270) {
      throw new IllegalArgumentException("0 <= rotation < 360, rotation % 90 == 0");
    } else if ((width * height * 3) / 2 != yuv.length) {
      throw new IOException("provided width and height don't jive with the data length (" +
                            yuv.length + "). Width: " + width + " height: " + height +
                            " = data length: " + (width * height * 3) / 2);
    }

    final byte[]  output    = new byte[yuv.length];
    final int     frameSize = width * height;
    final boolean swap      = rotation % 180 != 0;
    final boolean xflip     = flipHorizontal ? rotation % 270 == 0 : rotation % 270 != 0;
    final boolean yflip     = rotation >= 180;

    for (int j = 0; j < height; j++) {
      for (int i = 0; i < width; i++) {
        final int yIn = j * width + i;
        final int uIn = frameSize + (j >> 1) * width + (i & ~1);
        final int vIn = uIn       + 1;

        final int wOut     = swap ? height              : width;
        final int hOut     = swap ? width               : height;
        final int iSwapped = swap ? j                   : i;
        final int jSwapped = swap ? i                   : j;
        final int iOut     = xflip ? wOut - iSwapped - 1 : iSwapped;
        final int jOut     = yflip ? hOut - jSwapped - 1 : jSwapped;

        final int yOut = jOut * wOut + iOut;
        final int uOut = frameSize + (jOut >> 1) * wOut + (iOut & ~1);
        final int vOut = uOut + 1;

        output[yOut] = (byte)(0xff & yuv[yIn]);
        output[uOut] = (byte)(0xff & yuv[uIn]);
        output[vOut] = (byte)(0xff & yuv[vIn]);
      }
    }
    return output;
  }

  private static Pair<Integer, Integer> clampDimensions(int inWidth, int inHeight, int maxWidth, int maxHeight) {
    if (inWidth > maxWidth || inHeight > maxHeight) {
      final float aspectWidth, aspectHeight;

      if (inWidth == 0 || inHeight == 0) {
        aspectWidth  = maxWidth;
        aspectHeight = maxHeight;
      } else if (inWidth >= inHeight) {
        aspectWidth  = maxWidth;
        aspectHeight = (aspectWidth / inWidth) * inHeight;
      } else {
        aspectHeight = maxHeight;
        aspectWidth  = (aspectHeight / inHeight) * inWidth;
      }

      return new Pair<>(Math.round(aspectWidth), Math.round(aspectHeight));
    } else {
      return new Pair<>(inWidth, inHeight);
    }
  }

  public static Bitmap createFromDrawable(final Drawable drawable, final int width, final int height) {
    final AtomicBoolean created = new AtomicBoolean(false);
    final Bitmap[]      result  = new Bitmap[1];

    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        if (drawable instanceof BitmapDrawable) {
          result[0] = ((BitmapDrawable) drawable).getBitmap();
        } else {
          int canvasWidth = drawable.getIntrinsicWidth();
          if (canvasWidth <= 0) canvasWidth = width;

          int canvasHeight = drawable.getIntrinsicHeight();
          if (canvasHeight <= 0) canvasHeight = height;

          Bitmap bitmap;

          try {
            bitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
          } catch (Exception e) {
            Log.w(TAG, e);
            bitmap = null;
          }

          result[0] = bitmap;
        }

        synchronized (result) {
          created.set(true);
          result.notifyAll();
        }
      }
    };

    Util.runOnMain(runnable);

    synchronized (result) {
      while (!created.get()) Util.wait(result, 0);
      return result[0];
    }
  }

  public static int getMaxTextureSize() {
    final int IMAGE_MAX_BITMAP_DIMENSION = 2048;

    EGL10 egl = (EGL10) EGLContext.getEGL();
    EGLDisplay display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);

    int[] version = new int[2];
    egl.eglInitialize(display, version);

    int[] totalConfigurations = new int[1];
    egl.eglGetConfigs(display, null, 0, totalConfigurations);

    EGLConfig[] configurationsList = new EGLConfig[totalConfigurations[0]];
    egl.eglGetConfigs(display, configurationsList, totalConfigurations[0], totalConfigurations);

    int[] textureSize = new int[1];
    int maximumTextureSize = 0;

    for (int i = 0; i < totalConfigurations[0]; i++) {
      egl.eglGetConfigAttrib(display, configurationsList[i], EGL10.EGL_MAX_PBUFFER_WIDTH, textureSize);

      if (maximumTextureSize < textureSize[0])
        maximumTextureSize = textureSize[0];
    }

    egl.eglTerminate(display);

    return Math.max(maximumTextureSize, IMAGE_MAX_BITMAP_DIMENSION);
  }
}
