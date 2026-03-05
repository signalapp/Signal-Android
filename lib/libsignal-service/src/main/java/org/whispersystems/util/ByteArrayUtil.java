package org.whispersystems.util;

public final class ByteArrayUtil {

  private ByteArrayUtil() {
  }

  public static byte[] xor(byte[] a, byte[] b) {
    if (a.length != b.length) {
      throw new AssertionError("XOR length mismatch");
    }

    byte[] out = new byte[a.length];

    for (int i = a.length - 1; i >= 0; i--) {
      out[i] = (byte) (a[i] ^ b[i]);
    }

    return out;
  }

  public static byte[] concat(byte[] a, byte[] b) {
    byte[] result = new byte[a.length + b.length];
    System.arraycopy(a, 0, result, 0, a.length);
    System.arraycopy(b, 0, result, a.length, b.length);
    return result;
  }
}
