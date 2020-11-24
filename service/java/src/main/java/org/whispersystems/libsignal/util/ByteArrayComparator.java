package org.whispersystems.libsignal.util;

public abstract class ByteArrayComparator {

  protected int compare(byte[] left, byte[] right) {
    for (int i = 0, j = 0; i < left.length && j < right.length; i++, j++) {
      int a = (left[i] & 0xff);
      int b = (right[j] & 0xff);

      if (a != b) {
        return a - b;
      }
    }

    return left.length - right.length;
  }

}
