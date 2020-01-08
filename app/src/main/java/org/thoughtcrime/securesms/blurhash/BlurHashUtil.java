/**
 * Source: https://github.com/hsch/blurhash-java
 *
 * Copyright (c) 2019 Hendrik Schnepel
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.thoughtcrime.securesms.blurhash;

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
