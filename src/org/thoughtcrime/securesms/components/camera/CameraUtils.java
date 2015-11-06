package org.thoughtcrime.securesms.components.camera;

import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

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
