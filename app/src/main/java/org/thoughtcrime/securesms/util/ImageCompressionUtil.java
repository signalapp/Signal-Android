package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.mms.GlideApp;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.ExecutionException;

public final class ImageCompressionUtil {

  private static final String TAG = Log.tag(ImageCompressionUtil.class);

  private ImageCompressionUtil () {}

  /**
   * A result satisfying the provided constraints, or null if they could not be met.
   */
  @WorkerThread
  public static @Nullable Result compressWithinConstraints(@NonNull Context context,
                                                           @NonNull String mimeType,
                                                           @NonNull Object glideModel,
                                                           int maxDimension,
                                                           int maxBytes,
                                                           @IntRange(from = 0, to = 100) int quality)
      throws BitmapDecodingException
  {
    Result result = compress(context, mimeType, glideModel, maxDimension, quality);

    if (result.getData().length <= maxBytes) {
      return result;
    } else {
      return null;
    }
  }

  /**
   * Compresses the image to match the requested parameters.
   */
  @WorkerThread
  public static @NonNull Result compress(@NonNull Context context,
                                          @NonNull String mimeType,
                                          @NonNull Object glideModel,
                                          int maxDimension,
                                          @IntRange(from = 0, to = 100) int quality)
      throws BitmapDecodingException
  {
    Bitmap scaledBitmap;

    try {
      scaledBitmap = GlideApp.with(context.getApplicationContext())
                             .asBitmap()
                             .load(glideModel)
                             .skipMemoryCache(true)
                             .diskCacheStrategy(DiskCacheStrategy.NONE)
                             .centerInside()
                             .submit(maxDimension, maxDimension)
                             .get();
    } catch (ExecutionException | InterruptedException e) {
      if (e.getCause() instanceof GlideException) {
        ((GlideException) e.getCause()).logRootCauses(TAG);
      }
      throw new BitmapDecodingException(e);
    }

    if (scaledBitmap == null) {
      throw new BitmapDecodingException("Unable to decode image");
    }

    ByteArrayOutputStream output = new ByteArrayOutputStream();
    Bitmap.CompressFormat format = mimeTypeToCompressFormat(mimeType);
    scaledBitmap.compress(format, quality, output);

    byte[] data = output.toByteArray();

    return new Result(data, compressFormatToMimeType(format), scaledBitmap.getWidth(), scaledBitmap.getHeight());
  }

  private static @NonNull Bitmap.CompressFormat mimeTypeToCompressFormat(@NonNull String mimeType) {
    if (MediaUtil.isJpegType(mimeType) ||
        MediaUtil.isHeicType(mimeType) ||
        MediaUtil.isHeifType(mimeType) ||
        MediaUtil.isAvifType(mimeType) ||
        MediaUtil.isVideoType(mimeType)) {
      return Bitmap.CompressFormat.JPEG;
    } else {
      return Bitmap.CompressFormat.PNG;
    }
  }

  private static @NonNull String compressFormatToMimeType(@NonNull Bitmap.CompressFormat format) {
    switch (format) {
      case JPEG:
        return MediaUtil.IMAGE_JPEG;
      case PNG:
        return MediaUtil.IMAGE_PNG;
      default:
        throw new AssertionError("Unsupported format!");
    }
  }

  public static final class Result {
    private final byte[] data;
    private final String mimeType;
    private final int    height;
    private final int    width;

    public Result(@NonNull byte[] data, @NonNull String mimeType, int width, int height) {
      this.data     = data;
      this.mimeType = mimeType;
      this.width    = width;
      this.height   = height;
    }

    public byte[] getData() {
      return data;
    }

    public @NonNull String getMimeType() {
      return mimeType;
    }

    public int getWidth() {
      return width;
    }

    public int getHeight() {
      return height;
    }
  }
}
