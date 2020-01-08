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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.InputStream;

import static org.thoughtcrime.securesms.blurhash.BlurHashUtil.linearTosRGB;
import static org.thoughtcrime.securesms.blurhash.BlurHashUtil.max;
import static org.thoughtcrime.securesms.blurhash.BlurHashUtil.sRGBToLinear;
import static org.thoughtcrime.securesms.blurhash.BlurHashUtil.signPow;

public final class BlurHashEncoder {

  private BlurHashEncoder() {
  }

  public static @Nullable String encode(InputStream inputStream) {
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inSampleSize = 16;

    Bitmap bitmap = BitmapFactory.decodeStream(inputStream, null, options);
    if (bitmap == null) return null;

    String hash = encode(bitmap);

    bitmap.recycle();

    return hash;
  }

  public static @Nullable String encode(@NonNull Bitmap bitmap) {
    return encode(bitmap, 4, 3);
  }

  static String encode(Bitmap bitmap, int componentX, int componentY) {
    int width    = bitmap.getWidth();
    int height   = bitmap.getHeight();
    int[] pixels = new int[width * height];
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
    return encode(pixels, width, height, componentX, componentY);
  }

  private static String encode(int[] pixels, int width, int height, int componentX, int componentY) {

    if (componentX < 1 || componentX > 9 || componentY < 1 || componentY > 9) {
      throw new IllegalArgumentException("Blur hash must have between 1 and 9 components");
    }
    if (width * height != pixels.length) {
      throw new IllegalArgumentException("Width and height must match the pixels array");
    }

    double[][] factors = new double[componentX * componentY][3];
    for (int j = 0; j < componentY; j++) {
      for (int i = 0; i < componentX; i++) {
        double normalisation = i == 0 && j == 0 ? 1 : 2;
        applyBasisFunction(pixels, width, height,
            normalisation, i, j,
            factors, j * componentX + i);
      }
    }

    char[] hash = new char[1 + 1 + 4 + 2 * (factors.length - 1)]; // size flag + max AC + DC + 2 * AC components

    long sizeFlag = componentX - 1 + (componentY - 1) * 9;
    Base83.encode(sizeFlag, 1, hash, 0);

    double maximumValue;
    if (factors.length > 1) {
      double actualMaximumValue    = max(factors, 1, factors.length);
      double quantisedMaximumValue = Math.floor(Math.max(0, Math.min(82, Math.floor(actualMaximumValue * 166 - 0.5))));
      maximumValue = (quantisedMaximumValue + 1) / 166;
      Base83.encode(Math.round(quantisedMaximumValue), 1, hash, 1);
    } else {
      maximumValue = 1;
      Base83.encode(0, 1, hash, 1);
    }

    double[] dc = factors[0];
    Base83.encode(encodeDC(dc), 4, hash, 2);

    for (int i = 1; i < factors.length; i++) {
      Base83.encode(encodeAC(factors[i], maximumValue), 2, hash, 6 + 2 * (i - 1));
    }
    return new String(hash);
  }

  private static void applyBasisFunction(int[] pixels, int width, int height,
                                         double normalisation, int i, int j,
                                         double[][] factors, int index)
  {
    double r = 0, g = 0, b = 0;
    for (int x = 0; x < width; x++) {
      for (int y = 0; y < height; y++) {
        double basis = normalisation
                     * Math.cos((Math.PI * i * x) / width)
                     * Math.cos((Math.PI * j * y) / height);
        int pixel = pixels[y * width + x];
        r += basis * sRGBToLinear((pixel >> 16) & 0xff);
        g += basis * sRGBToLinear((pixel >> 8)  & 0xff);
        b += basis * sRGBToLinear( pixel        & 0xff);
      }
    }
    double scale = 1.0 / (width * height);
    factors[index][0] = r * scale;
    factors[index][1] = g * scale;
    factors[index][2] = b * scale;
  }

  private static long encodeDC(double[] value) {
    long r = linearTosRGB(value[0]);
    long g = linearTosRGB(value[1]);
    long b = linearTosRGB(value[2]);
    return (r << 16) + (g << 8) + b;
  }

  private static long encodeAC(double[] value, double maximumValue) {
    double quantR = Math.floor(Math.max(0, Math.min(18, Math.floor(signPow(value[0] / maximumValue, 0.5) * 9 + 9.5))));
    double quantG = Math.floor(Math.max(0, Math.min(18, Math.floor(signPow(value[1] / maximumValue, 0.5) * 9 + 9.5))));
    double quantB = Math.floor(Math.max(0, Math.min(18, Math.floor(signPow(value[2] / maximumValue, 0.5) * 9 + 9.5))));
    return Math.round(quantR * 19 * 19 + quantG * 19 + quantB);
  }

}
