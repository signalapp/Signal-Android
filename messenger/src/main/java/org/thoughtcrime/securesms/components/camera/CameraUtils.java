package org.thoughtcrime.securesms.components.camera;

import android.app.Activity;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.DisplayMetrics;
import org.thoughtcrime.securesms.logging.Log;
import android.view.Surface;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

@SuppressWarnings("deprecation")
public class CameraUtils {
  private static final String TAG = CameraUtils.class.getSimpleName();
  /*
   * modified from: https://github.com/commonsguy/cwac-camera/blob/master/camera/src/com/commonsware/cwac/camera/CameraUtils.java
   */
  public static @Nullable Size getPreferredPreviewSize(int displayOrientation,
                                                       int width,
                                                       int height,
                                                       @NonNull Parameters parameters) {
    final int    targetWidth  = displayOrientation % 180 == 90 ? height : width;
    final int    targetHeight = displayOrientation % 180 == 90 ? width  : height;
    final double targetRatio  = (double) targetWidth / targetHeight;

    Log.d(TAG, String.format("getPreferredPreviewSize(%d, %d, %d) -> target %dx%d, AR %.02f",
                             displayOrientation, width, height,
                             targetWidth, targetHeight, targetRatio));

    List<Size> sizes     = parameters.getSupportedPreviewSizes();
    List<Size> ideals    = new LinkedList<>();
    List<Size> bigEnough = new LinkedList<>();

    for (Size size : sizes) {
      Log.d(TAG, String.format("  %dx%d (%.02f)", size.width, size.height, (float)size.width / size.height));

      if (size.height == size.width * targetRatio && size.height >= targetHeight && size.width >= targetWidth) {
        ideals.add(size);
        Log.d(TAG, "    (ideal ratio)");
      } else if (size.width >= targetWidth && size.height >= targetHeight) {
        bigEnough.add(size);
        Log.d(TAG, "    (good size, suboptimal ratio)");
      }
    }

    if      (!ideals.isEmpty())    return Collections.min(ideals, new AreaComparator());
    else if (!bigEnough.isEmpty()) return Collections.min(bigEnough, new AspectRatioComparator(targetRatio));
    else                           return Collections.max(sizes, new AreaComparator());
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

  private static class AreaComparator implements Comparator<Size> {
    @Override
    public int compare(Size lhs, Size rhs) {
      return Long.signum(lhs.width * lhs.height - rhs.width * rhs.height);
    }
  }

  private static class AspectRatioComparator extends AreaComparator {
    private final double target;
    public AspectRatioComparator(double target) {
      this.target = target;
    }

    @Override
    public int compare(Size lhs, Size rhs) {
      final double lhsDiff = Math.abs(target - (double) lhs.width / lhs.height);
      final double rhsDiff = Math.abs(target - (double) rhs.width / rhs.height);
      if      (lhsDiff < rhsDiff)  return -1;
      else if (lhsDiff > rhsDiff)  return 1;
      else                         return super.compare(lhs, rhs);
    }
  }
}
