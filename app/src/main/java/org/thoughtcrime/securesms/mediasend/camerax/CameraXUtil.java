package org.thoughtcrime.securesms.mediasend.camerax;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.os.Build;
import android.util.Pair;
import android.util.Rational;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.camera.camera2.internal.compat.CameraManagerCompat;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageProxy;

import org.signal.core.util.Stopwatch;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.util.BitmapDecodingException;
import org.thoughtcrime.securesms.util.BitmapUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Locale;

public class CameraXUtil {

  private static final String TAG = Log.tag(CameraXUtil.class);

  private static final int[] CAMERA_HARDWARE_LEVEL_ORDERING = new int[]{CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY,
                                                                        CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED,
                                                                        CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL};

  @RequiresApi(24)
  private static final int[] CAMERA_HARDWARE_LEVEL_ORDERING_24 = new int[]{CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY,
                                                                           CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED,
                                                                           CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL,
                                                                           CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3};

  @RequiresApi(28)
  private static final int[] CAMERA_HARDWARE_LEVEL_ORDERING_28 = new int[]{CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY,
                                                                           CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED,
                                                                           CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL,
                                                                           CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL,
                                                                           CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3};

  @SuppressWarnings("SuspiciousNameCombination")
  public static ImageResult toJpeg(@NonNull ImageProxy image, boolean flip) throws IOException {
    ImageProxy.PlaneProxy[] planes   = image.getPlanes();
    ByteBuffer              buffer   = planes[0].getBuffer();
    Rect                    cropRect = shouldCropImage(image) ? image.getCropRect() : null;
    byte[]                  data     = new byte[buffer.capacity()];
    int                     rotation = image.getImageInfo().getRotationDegrees();

    buffer.get(data);

    try {
      Pair<Integer, Integer> dimens = BitmapUtil.getDimensions(new ByteArrayInputStream(data));

      if (dimens.first != image.getWidth() && dimens.second != image.getHeight()) {
        Log.w(TAG, String.format(Locale.ENGLISH, "Decoded image dimensions differed from stated dimensions! Stated: %d x %d, Decoded: %d x %d",
                                                  image.getWidth(), image.getHeight(), dimens.first, dimens.second));
        Log.w(TAG, "Ignoring the stated rotation and rotating the crop rect 90 degrees (stated rotation is " + rotation + " degrees).");

        rotation = 0;

        if (cropRect != null) {
          cropRect = new Rect(cropRect.top, cropRect.left, cropRect.bottom, cropRect.right);
        }
      }
    } catch (BitmapDecodingException e) {
      Log.w(TAG, "Failed to decode!", e);
    }

    if (cropRect != null || rotation != 0 || flip) {
      data = transformByteArray(data, cropRect, rotation, flip);
    }

    int width  = cropRect != null ? (cropRect.right - cropRect.left) : image.getWidth();
    int height = cropRect != null ? (cropRect.bottom - cropRect.top) : image.getHeight();

    if (rotation == 90 || rotation == 270) {
      int swap = width;

      width  = height;
      height = swap;
    }

    return new ImageResult(data, width, height);
  }

  public static boolean isSupported() {
    return !CameraXModelBlocklist.isBlocklisted();
  }

  public static int toCameraDirectionInt(CameraSelector cameraSelector) {
    if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
      return Camera.CameraInfo.CAMERA_FACING_FRONT;
    } else {
      return Camera.CameraInfo.CAMERA_FACING_BACK;
    }
  }

  public static CameraSelector toCameraSelector(@CameraSelector.LensFacing int cameraDirectionInt) {
    if (cameraDirectionInt == Camera.CameraInfo.CAMERA_FACING_FRONT) {
      return CameraSelector.DEFAULT_FRONT_CAMERA;
    } else {
      return CameraSelector.DEFAULT_BACK_CAMERA;
    }
  }

  public static @ImageCapture.CaptureMode int getOptimalCaptureMode() {
    return FastCameraModels.contains(Build.MODEL) ? ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
                                                  : ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY;
  }

  public static int getIdealResolution(int displayWidth, int displayHeight) {
    int maxDisplay = Math.max(displayWidth, displayHeight);
    return Math.max(maxDisplay, 1920);
  }

  public static @NonNull Size buildResolutionForRatio(int longDimension, @NonNull Rational ratio, boolean isPortrait) {
    int shortDimension = longDimension * ratio.getDenominator() / ratio.getNumerator();

    if (isPortrait) {
      return new Size(shortDimension, longDimension);
    } else {
      return new Size(longDimension, shortDimension);
    }
  }

  private static byte[] transformByteArray(@NonNull byte[] data, @Nullable Rect cropRect, int rotation, boolean flip) throws IOException {
    Stopwatch stopwatch = new Stopwatch("transform");
    Bitmap in;

    if (cropRect != null) {
      BitmapRegionDecoder decoder = BitmapRegionDecoder.newInstance(data, 0, data.length, false);
      in = decoder.decodeRegion(cropRect, new BitmapFactory.Options());
      decoder.recycle();
      stopwatch.split("crop");
    } else {
      in = BitmapFactory.decodeByteArray(data, 0, data.length);
    }

    Bitmap out = in;

    if (rotation != 0 || flip) {
      Matrix matrix = new Matrix();
      matrix.postRotate(rotation);

      if (flip) {
        matrix.postScale(-1, 1);
        matrix.postTranslate(in.getWidth(), 0);
      }

      out = Bitmap.createBitmap(in, 0, 0, in.getWidth(), in.getHeight(), matrix, true);
    }

    byte[] transformedData = toJpegBytes(out);
    stopwatch.split("transcode");

    in.recycle();
    out.recycle();

    stopwatch.stop(TAG);

    return transformedData;
  }

  private static boolean shouldCropImage(@NonNull ImageProxy image) {
    Size sourceSize = new Size(image.getWidth(), image.getHeight());
    Size targetSize = new Size(image.getCropRect().width(), image.getCropRect().height());

    return !targetSize.equals(sourceSize);
  }

  private static byte[] toJpegBytes(@NonNull Bitmap bitmap) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();

    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)) {
      throw new IOException("Failed to compress bitmap.");
    }

    return out.toByteArray();
  }

  public static boolean isMixedModeSupported(@NonNull Context context) {
    return getLowestSupportedHardwareLevel(context) != CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY;
  }

  public static int getLowestSupportedHardwareLevel(@NonNull Context context) {
    @SuppressLint("RestrictedApi") CameraManager cameraManager = CameraManagerCompat.from(context).unwrap();

    try {
      int supported = maxHardwareLevel();

      for (String cameraId : cameraManager.getCameraIdList()) {
        Integer hwLevel = null;

        try {
          hwLevel = cameraManager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
        } catch (NullPointerException e) {
          // redmi device crash, assume lowest
        }

        if (hwLevel == null || hwLevel == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
          return CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY;
        }

        supported = smallerHardwareLevel(supported, hwLevel);
      }

      return supported;
    } catch (CameraAccessException e) {
      Log.w(TAG, "Failed to enumerate cameras", e);

      return CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY;
    }
  }

  private static int maxHardwareLevel() {
    if (Build.VERSION.SDK_INT >= 24) return CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3;
    else                             return CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL;
  }

  private static int smallerHardwareLevel(int levelA, int levelB) {

    int[] hardwareInfoOrdering = getHardwareInfoOrdering();
    for (int hwInfo : hardwareInfoOrdering) {
      if (levelA == hwInfo || levelB == hwInfo) return hwInfo;
    }

    return CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY;
  }

  private static int[] getHardwareInfoOrdering() {
    if      (Build.VERSION.SDK_INT >= 28) return CAMERA_HARDWARE_LEVEL_ORDERING_28;
    else if (Build.VERSION.SDK_INT >= 24) return CAMERA_HARDWARE_LEVEL_ORDERING_24;
    else                                  return CAMERA_HARDWARE_LEVEL_ORDERING;
  }

  public static class ImageResult {
    private final byte[] data;
    private final int    width;
    private final int    height;

    public ImageResult(@NonNull byte[] data, int width, int height) {
      this.data   = data;
      this.width  = width;
      this.height = height;
    }

    public byte[] getData() {
      return data;
    }

    public int getWidth() {
      return width;
    }

    public int getHeight() {
      return height;
    }
  }
}
