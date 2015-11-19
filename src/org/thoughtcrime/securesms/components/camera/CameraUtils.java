package org.thoughtcrime.securesms.components.camera;

import android.app.Activity;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Size;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@SuppressWarnings("deprecation")
public class CameraUtils {
  /*
   * modified from: https://github.com/commonsguy/cwac-camera/blob/master/camera/src/com/commonsware/cwac/camera/CameraUtils.java
   */
  public static @Nullable Size getPreferredPreviewSize(int displayOrientation,
                                                       int width,
                                                       int height,
                                                       @NonNull Camera camera) {
    Log.w("CameraUtils", String.format("getPreferredPreviewSize(%d, %d, %d)", displayOrientation, width, height));
    double targetRatio = (double)width / height;
    Size   optimalSize = null;
    double minDiff     = Double.MAX_VALUE;

    if (displayOrientation == 90 || displayOrientation == 270) {
      targetRatio = (double)height / width;
    }

    List<Size> sizes = camera.getParameters().getSupportedPreviewSizes();

    Collections.sort(sizes, Collections.reverseOrder(new SizeComparator()));

    for (Size size : sizes) {
      double ratio = (double)size.width / size.height;

      if (Math.abs(ratio - targetRatio) < minDiff) {
        optimalSize = size;
        minDiff     = Math.abs(ratio - targetRatio);
      }
    }

    return optimalSize;
  }

  // based on
  // http://developer.android.com/reference/android/hardware/Camera.html#setDisplayOrientation(int)
  // and http://stackoverflow.com/a/10383164/115145
  public static int getCameraDisplayOrientation(@NonNull Activity activity,
                                                @NonNull CameraInfo info)
  {
    int            rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
    int            degrees  = 0;
    DisplayMetrics dm       = new DisplayMetrics();

    activity.getWindowManager().getDefaultDisplay().getMetrics(dm);

    switch (rotation) {
    case Surface.ROTATION_0:   degrees = 0;   break;
    case Surface.ROTATION_90:  degrees = 90;  break;
    case Surface.ROTATION_180: degrees = 180; break;
    case Surface.ROTATION_270: degrees = 270; break;
    }

    if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
      return (360 - ((info.orientation + degrees) % 360)) % 360;
    } else {
      return (info.orientation - degrees + 360) % 360;
    }
  }

  private static class SizeComparator implements Comparator<Size> {
    @Override
    public int compare(Size lhs, Size rhs) {
      int left  = lhs.width * lhs.height;
      int right = rhs.width * rhs.height;

      if (left < right) return -1;
      if (left > right) return 1;
      else              return 0;
    }
  }
}
