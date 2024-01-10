/**
 * Source: https://github.com/hsch/blurhash-java
 * <p>
 * Copyright (c) 2019 Hendrik Schnepel
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.thoughtcrime.securesms.blurhash;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

final class Base83 {

  private static final int MAX_LENGTH = 90;

//  sorted Alphabets base 83 in "ascii" ->
  private static final char[] ALPHABET = "#$%*+,-.0123456789:;=?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[]^_abcdefghijklmnopqrstuvwxyz{|}~".toCharArray();

  private static int indexOf(char[] a, char key) {
    return binarySearch(a,key);
  }

  public static int binarySearch(@NonNull char[] a, char key) { // a[] should be sorted
    int low  = 0;
    int mid;
    int high = a.length;

    while (low <= high) {
      mid = (int) (low + high) / 2;
      if (key == a[mid]) {
        return mid;
      } else if (key > a[mid]) {
        low = mid + 1;
      } else if (key < a[mid]) {
        high = mid - 1;
      }
    }

    return -1;
  }

  static void encode(long value, int length, char[] buffer, int offset) {
    int exp = 1;
    for (int i = 1; i <= length; i++, exp *= 83) {
      int digit = (int) (value / exp % 83);
      buffer[offset + length - i] = ALPHABET[digit];
    }
  }

  static int decode(String value, int fromInclusive, int toExclusive) {
    int    result = 0;
    char[] chars  = value.toCharArray();
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

