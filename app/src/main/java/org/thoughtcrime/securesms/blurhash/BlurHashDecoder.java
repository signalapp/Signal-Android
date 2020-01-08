/**
 * Source: https://github.com/woltapp/blurhash
 *
 * Copyright (c) 2018 Wolt Enterprises
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

import android.graphics.Bitmap;
import android.graphics.Color;

import androidx.annotation.Nullable;

import static org.thoughtcrime.securesms.blurhash.BlurHashUtil.linearTosRGB;
import static org.thoughtcrime.securesms.blurhash.BlurHashUtil.sRGBToLinear;
import static org.thoughtcrime.securesms.blurhash.BlurHashUtil.signPow;

class BlurHashDecoder {

  static @Nullable Bitmap decode(@Nullable String blurHash, int width, int height) {
    return decode(blurHash, width, height, 1f);
  }

  static @Nullable Bitmap decode(@Nullable String blurHash, int width, int height, double punch) {

    if (blurHash == null || blurHash.length() < 6) {
      return null;
    }

    int numCompEnc = Base83.decode(blurHash, 0, 1);
    int numCompX   = (numCompEnc % 9) + 1;
    int numCompY   = (numCompEnc / 9) + 1;

    if (blurHash.length() != 4 + 2 * numCompX * numCompY) {
      return null;
    }

    int        maxAcEnc = Base83.decode(blurHash, 1, 2);
    double     maxAc    = (maxAcEnc + 1) / 166f;
    double[][] colors   = new double[numCompX * numCompY][];
    for (int i = 0; i < colors.length; i++) {
      if (i == 0) {
        int colorEnc = Base83.decode(blurHash, 2, 6);
        colors[i] = decodeDc(colorEnc);
      } else {
        int from = 4 + i * 2;
        int colorEnc = Base83.decode(blurHash, from, from + 2);
        colors[i] = decodeAc(colorEnc, maxAc * punch);
      }
    }

    return composeBitmap(width, height, numCompX, numCompY, colors);
  }

  private static double[] decodeDc(int colorEnc) {
    int r = colorEnc >> 16;
    int g = (colorEnc >> 8) & 255;
    int b = colorEnc & 255;
    return new double[] {sRGBToLinear(r),
                         sRGBToLinear(g),
                         sRGBToLinear(b)};
  }

  private static double[] decodeAc(int value, double maxAc) {
    int r = value / (19 * 19);
    int g = (value / 19) % 19;
    int b = value % 19;
    return new double[]{
        signPow((r - 9) / 9.0f, 2f) * maxAc,
        signPow((g - 9) / 9.0f, 2f) * maxAc,
        signPow((b - 9) / 9.0f, 2f) * maxAc
    };
  }

  private static Bitmap composeBitmap(int width, int height, int numCompX, int numCompY, double[][] colors) {
    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {

        double r = 0f;
        double g = 0f;
        double b = 0f;

        for (int j = 0; j < numCompY; j++) {
          for (int i = 0; i < numCompX; i++) {
            double basis = (Math.cos(Math.PI * x * i / width) * Math.cos(Math.PI * y * j / height));
            double[] color = colors[j * numCompX + i];
            r += color[0] * basis;
            g += color[1] * basis;
            b += color[2] * basis;
          }
        }
        bitmap.setPixel(x, y, Color.rgb((int) linearTosRGB(r), (int) linearTosRGB(g), (int) linearTosRGB(b)));
      }
    }

    return bitmap;
  }
}
