package org.thoughtcrime.securesms.components.camera;

import android.annotation.TargetApi;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.os.Build.VERSION;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@SuppressWarnings("deprecation")
public class CameraUtils {
  @TargetApi(11)
  public static @Nullable Size getPreferredPreviewSize(int orientation, int width, int height, @NonNull Camera camera) {
    final Parameters parameters    = camera.getParameters();
    final Size       preferredSize = VERSION.SDK_INT > 11
                                   ? parameters.getPreferredPreviewSizeForVideo()
                                   : null;

    return preferredSize == null ? getBestAspectPreviewSize(orientation, width, height, parameters)
                                 : preferredSize;
  }

  /*
   * modified from: https://github.com/commonsguy/cwac-camera/blob/master/camera/src/com/commonsware/cwac/camera/CameraUtils.java
   */
  public static @Nullable Size getBestAspectPreviewSize(int displayOrientation,
                                                        int width,
                                                        int height,
                                                        Parameters parameters) {
    double targetRatio = (double)width / height;
    Size   optimalSize = null;
    double minDiff     = Double.MAX_VALUE;

    if (displayOrientation == 90 || displayOrientation == 270) {
      targetRatio = (double)height / width;
    }

    List<Size> sizes = parameters.getSupportedPreviewSizes();

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
