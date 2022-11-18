package org.signal.core.util;

import androidx.annotation.ColorInt;
import androidx.annotation.FloatRange;

public final class ColorUtil {

  private ColorUtil() {}

  public static int blendARGB(@ColorInt int color1,
                              @ColorInt int color2,
                              @FloatRange(from = 0.0, to = 1.0) float ratio)
  {
    final float inverseRatio = 1 - ratio;

    float a = alpha(color1) * inverseRatio + alpha(color2) * ratio;
    float r = red(color1)   * inverseRatio + red(color2)   * ratio;
    float g = green(color1) * inverseRatio + green(color2) * ratio;
    float b = blue(color1)  * inverseRatio + blue(color2)  * ratio;

    return argb((int) a, (int) r, (int) g, (int) b);
  }

  private static int alpha(int color) {
    return color >>> 24;
  }

  private static int red(int color) {
    return (color >> 16) & 0xFF;
  }

  private static int green(int color) {
    return (color >> 8) & 0xFF;
  }

  private static int blue(int color) {
    return color & 0xFF;
  }

  private static int argb(int alpha, int red, int green, int blue) {
    return (alpha << 24) | (red << 16) | (green << 8) | blue;
  }
}
