package org.thoughtcrime.securesms.mediasend.camerax;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Build;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.camera.core.CameraX;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageProxy;

import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.Stopwatch;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class CameraXUtil {

  private static final String TAG = Log.tag(CameraXUtil.class);

  @RequiresApi(21)
  public static byte[] toJpegBytes(@NonNull ImageProxy image, int rotation, boolean flip) throws IOException {
    ImageProxy.PlaneProxy[] planes   = image.getPlanes();
    ByteBuffer              buffer   = planes[0].getBuffer();
    Rect                    cropRect = shouldCropImage(image) ? image.getCropRect() : null;
    byte[]                  data     = new byte[buffer.capacity()];

    buffer.get(data);

    if (cropRect != null || rotation != 0 || flip) {
      data = transformByteArray(data, cropRect, rotation, flip);
    }

    return data;
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
}
