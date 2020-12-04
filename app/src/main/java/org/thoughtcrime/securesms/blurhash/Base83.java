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

import androidx.annotation.Nullable;

final class Base83 {

  private static final int MAX_LENGTH = 90;

  private static final char[]ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#$%*+,-.:;=?@[]^_{|}~".toCharArray();

  private static int indexOf(char[] a, char key) {
    for (int i = 0; i < a.length; i++) {
      if (a[i] == key) {
        return i;
      }
    }
    return -1;
  }

  static void encode(long value, int length, char[] buffer, int offset) {
    int exp = 1;
    for (int i = 1; i <= length; i++, exp *= 83) {
      int digit = (int)(value / exp % 83);
      buffer[offset + length - i] = ALPHABET[digit];
    }
  }

  static int decode(String value, int fromInclusive, int toExclusive) {
    int result = 0;
    char[] chars = value.toCharArray();
    for (int i = fromInclusive; i < toExclusive; i++) {
      result = result * 83 + indexOf(ALPHABET, chars[i]);
    }
    return result;
  }

  static boolean isValid(@Nullable String value) {
    if (value == null) return false;
    final int length = value.length();

    if (length == 0 || length > MAX_LENGTH) return false;

    for (int i = 0; i < length; i++) {
      if (indexOf(ALPHABET, value.charAt(i)) == -1) return false;
    }

    return true;
  }

  private Base83() {
  }
}

