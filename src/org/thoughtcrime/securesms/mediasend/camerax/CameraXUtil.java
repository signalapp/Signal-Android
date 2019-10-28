package org.thoughtcrime.securesms.mediasend.camerax;

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
import android.util.Rational;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.camera.camera2.impl.compat.CameraManagerCompat;
import androidx.camera.core.CameraX;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageProxy;

import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mediasend.LegacyCameraModels;
import org.thoughtcrime.securesms.migrations.LegacyMigrationJob;
import org.thoughtcrime.securesms.util.Stopwatch;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class CameraXUtil {

  private static final String TAG = Log.tag(CameraXUtil.class);

  @RequiresApi(21)
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
  @RequiresApi(21)
  public static ImageResult toJpeg(@NonNull ImageProxy image, int rotation, boolean flip) throws IOException {
    ImageProxy.PlaneProxy[] planes   = image.getPlanes();
    ByteBuffer              buffer   = planes[0].getBuffer();
    Rect                    cropRect = shouldCropImage(image) ? image.getCropRect() : null;
    byte[]                  data     = new byte[buffer.capacity()];

    buffer.get(data);

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
    return Build.VERSION.SDK_INT >= 21 && !LegacyCameraModels.isLegacyCameraModel();
  }

  public static int toCameraDirectionInt(@Nullable CameraX.LensFacing facing) {
    if (facing == CameraX.LensFacing.FRONT) {
      return Camera.CameraInfo.CAMERA_FACING_FRONT;
    } else {
      return Camera.CameraInfo.CAMERA_FACING_BACK;
    }
  }

  public static @NonNull CameraX.LensFacing toLensFacing(int cameraDirectionInt) {
    if (cameraDirectionInt == Camera.CameraInfo.CAMERA_FACING_FRONT) {
      return CameraX.LensFacing.FRONT;
    } else {
      return CameraX.LensFacing.BACK;
    }
  }

  public static @NonNull ImageCapture.CaptureMode getOptimalCaptureMode() {
    return FastCameraModels.contains(Build.MODEL) ? ImageCapture.CaptureMode.MAX_QUALITY
                                                  : ImageCapture.CaptureMode.MIN_LATENCY;
  }

  public static int getIdealResolution(int displayWidth, int displayHeight) {
    int maxDisplay = Math.max(displayWidth, displayHeight);
    return Math.max(maxDisplay, 1920);
  }

  @TargetApi(21)
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

  @RequiresApi(21)
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

  @RequiresApi(21)
  public static boolean isMixedModeSupported(@NonNull Context context) {
    return getLowestSupportedHardwareLevel(context) != CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY;
  }

  @RequiresApi(21)
  public static int getLowestSupportedHardwareLevel(@NonNull Context context) {
    CameraManager cameraManager = CameraManagerCompat.from(context).unwrap();

    try {
      int supported = maxHardwareLevel();

      for (String cameraId : cameraManager.getCameraIdList()) {
        CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
        Integer               hwLevel         = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);

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

  @RequiresApi(21)
  private static int maxHardwareLevel() {
    if (Build.VERSION.SDK_INT >= 24) return CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3;
    else                             return CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL;
  }

  @RequiresApi(21)
  private static int smallerHardwareLevel(int levelA, int levelB) {

    int[] hardwareInfoOrdering = getHardwareInfoOrdering();
    for (int hwInfo : hardwareInfoOrdering) {
      if (levelA == hwInfo || levelB == hwInfo) return hwInfo;
    }

    return CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY;
  }

  @RequiresApi(21)
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
