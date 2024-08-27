package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import org.signal.core.util.ByteSize;
import org.signal.core.util.logging.Log;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.ExecutionException;

public final class ImageCompressionUtil {

  private static final String                  TAG                   = Log.tag(ImageCompressionUtil.class);
  private static final RequestListener<Bitmap> bitmapRequestListener = new RequestListener<>() {
    @Override
    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Bitmap> target, boolean isFirstResource) {
      if (e != null) {
        List<Throwable> causes = e.getRootCauses();
        for (int i = 0, size = causes.size(); i < size; i++) {
          Log.i(TAG, "Root cause (" + (i + 1) + " of " + size + ")", causes.get(i));
        }
      } else {
        Log.e(TAG, "Loading failed: " + model);
      }
      return false;
    }

    @Override
    public boolean onResourceReady(Bitmap resource, Object model, Target<Bitmap> target, DataSource dataSource, boolean isFirstResource) {
      return false;
    }
  };

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
                                         @Nullable String contentType,
                                         @NonNull Object glideModel,
                                         int maxDimension,
                                         @IntRange(from = 0, to = 100) int quality)
      throws BitmapDecodingException
  {
    Bitmap scaledBitmap;

    try {
      scaledBitmap = Glide.with(context.getApplicationContext())
                             .asBitmap()
                             .addListener(bitmapRequestListener)
                             .load(glideModel)
                             .skipMemoryCache(true)
                             .diskCacheStrategy(DiskCacheStrategy.NONE)
                             .centerInside()
                             .submit(maxDimension, maxDimension)
                             .get();
    } catch (ExecutionException | InterruptedException e) {
      Log.w(TAG, "Verbose logging to try to give all possible debug information for Glide issues. Exceptions below may be duplicated.", e);
      if (e.getCause() instanceof GlideException) {
        List<Throwable> rootCauses = ((GlideException) e.getCause()).getRootCauses();
        if (!rootCauses.isEmpty()) {
          for (int i = 0, size = rootCauses.size(); i < size; i++) {
            Log.w(TAG, "Root cause (" + (i + 1) + " of " + size + ")", rootCauses.get(i));
          }
        } else {
          Log.w(TAG, "Encountered GlideException with no root cause.", e.getCause());
        }
        List<Throwable> causes = ((GlideException) e.getCause()).getCauses();
        if (!causes.isEmpty()) {
          for (int i = 0, size = causes.size(); i < size; i++) {
            Log.w(TAG, "Caused by (" + (i + 1) + " of " + size + ")", causes.get(i));
          }
        } else {
          Log.w(TAG, "Encountered GlideException with no child cause.", e.getCause());
        }
      } else {
        Log.w(TAG, "Encountered non-GlideException.", e);
      }
      throw new BitmapDecodingException(e);
    }

    if (scaledBitmap == null) {
      throw new BitmapDecodingException("Unable to decode image");
    }

    ByteArrayOutputStream output = new ByteArrayOutputStream();
    Bitmap.CompressFormat format = mimeTypeToCompressFormat(contentType);
    scaledBitmap.compress(format, quality, output);

    byte[] data = output.toByteArray();

    Log.d(TAG, "[Input] mimeType: " + contentType + " [Output] format: " + format + ", maxDimension: " + maxDimension + ", quality: " + quality + ", size(KiB): " + new ByteSize(data.length).getInWholeKibiBytes());
    return new Result(data, compressFormatToMimeType(format), scaledBitmap.getWidth(), scaledBitmap.getHeight());
  }

  private static @NonNull Bitmap.CompressFormat mimeTypeToCompressFormat(@Nullable String mimeType) {
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
