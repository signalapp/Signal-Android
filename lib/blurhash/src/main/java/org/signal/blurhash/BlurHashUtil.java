/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.signal.blurhash;

final class BlurHashUtil {

  static double sRGBToLinear(long value) {
    double v = value / 255.0;
    if (v <= 0.04045) {
      return v / 12.92;
    } else {
      return Math.pow((v + 0.055) / 1.055, 2.4);
    }
  }

  static long linearTosRGB(double value) {
    double v = Math.max(0, Math.min(1, value));
    if (v <= 0.0031308) {
      return (long)(v * 12.92 * 255 + 0.5);
    } else {
      return (long)((1.055 * Math.pow(v, 1 / 2.4) - 0.055) * 255 + 0.5);
    }
  }

  static double signPow(double val, double exp) {
    return Math.copySign(Math.pow(Math.abs(val), exp), val);
  }

  static double max(double[][] values, int from, int endExclusive) {
    double result = Double.NEGATIVE_INFINITY;
    for (int i = from; i < endExclusive; i++) {
      for (int j = 0; j < values[i].length; j++) {
        double value = values[i][j];
        if (value > result) {
          result = value;
        }
      }
    }
    return result;
  }

  private BlurHashUtil() {
  }
}
