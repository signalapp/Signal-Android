/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */
package org.whispersystems.libsignal.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.ParseException;

public class ByteUtil {

  public static byte[] combine(byte[]... elements) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();

      for (byte[] element : elements) {
        baos.write(element);
      }

      return baos.toByteArray();
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  public static byte[][] split(byte[] input, int firstLength, int secondLength) {
    byte[][] parts = new byte[2][];

    parts[0] = new byte[firstLength];
    System.arraycopy(input, 0, parts[0], 0, firstLength);

    parts[1] = new byte[secondLength];
    System.arraycopy(input, firstLength, parts[1], 0, secondLength);

    return parts;
  }

  public static byte[][] split(byte[] input, int firstLength, int secondLength, int thirdLength)
      throws ParseException
  {
    if (input == null || firstLength < 0 || secondLength < 0 || thirdLength < 0 ||
        input.length < firstLength + secondLength + thirdLength)
    {
      throw new ParseException("Input too small: " + (input == null ? null : Hex.toString(input)), 0);
    }

    byte[][] parts = new byte[3][];

    parts[0] = new byte[firstLength];
    System.arraycopy(input, 0, parts[0], 0, firstLength);

    parts[1] = new byte[secondLength];
    System.arraycopy(input, firstLength, parts[1], 0, secondLength);

    parts[2] = new byte[thirdLength];
    System.arraycopy(input, firstLength + secondLength, parts[2], 0, thirdLength);

    return parts;
  }

  public static byte[] trim(byte[] input, int length) {
    byte[] result = new byte[length];
    System.arraycopy(input, 0, result, 0, result.length);

    return result;
  }

  public static byte[] copyFrom(byte[] input) {
    byte[] output = new byte[input.length];
    System.arraycopy(input, 0, output, 0, output.length);

    return output;
  }

  public static byte intsToByteHighAndLow(int highValue, int lowValue) {
    return (byte)((highValue << 4 | lowValue) & 0xFF);
  }

  public static int highBitsToInt(byte value) {
    return (value & 0xFF) >> 4;
  }

  public static int lowBitsToInt(byte value) {
    return (value & 0xF);
  }

  public static int highBitsToMedium(int value) {
    return (value >> 12);
  }

  public static int lowBitsToMedium(int value) {
    return (value & 0xFFF);
  }

  public static byte[] shortToByteArray(int value) {
    byte[] bytes = new byte[2];
    shortToByteArray(bytes, 0, value);
    return bytes;
  }

  public static int shortToByteArray(byte[] bytes, int offset, int value) {
    bytes[offset+1] = (byte)value;
    bytes[offset]   = (byte)(value >> 8);
    return 2;
  }

  public static int shortToLittleEndianByteArray(byte[] bytes, int offset, int value) {
    bytes[offset]   = (byte)value;
    bytes[offset+1] = (byte)(value >> 8);
    return 2;
  }

  public static byte[] mediumToByteArray(int value) {
    byte[] bytes = new byte[3];
    mediumToByteArray(bytes, 0, value);
    return bytes;
  }

  public static int mediumToByteArray(byte[] bytes, int offset, int value) {
    bytes[offset + 2] = (byte)value;
    bytes[offset + 1] = (byte)(value >> 8);
    bytes[offset]     = (byte)(value >> 16);
    return 3;
  }

  public static byte[] intToByteArray(int value) {
    byte[] bytes = new byte[4];
    intToByteArray(bytes, 0, value);
    return bytes;
  }

  public static int intToByteArray(byte[] bytes, int offset, int value) {
    bytes[offset + 3] = (byte)value;
    bytes[offset + 2] = (byte)(value >> 8);
    bytes[offset + 1] = (byte)(value >> 16);
    bytes[offset]     = (byte)(value >> 24);
    return 4;
  }

  public static int intToLittleEndianByteArray(byte[] bytes, int offset, int value) {
    bytes[offset]   = (byte)value;
    bytes[offset+1] = (byte)(value >> 8);
    bytes[offset+2] = (byte)(value >> 16);
    bytes[offset+3] = (byte)(value >> 24);
    return 4;
  }

  public static byte[] longToByteArray(long l) {
    byte[] bytes = new byte[8];
    longToByteArray(bytes, 0, l);
    return bytes;
  }

  public static int longToByteArray(byte[] bytes, int offset, long value) {
    bytes[offset + 7] = (byte)value;
    bytes[offset + 6] = (byte)(value >> 8);
    bytes[offset + 5] = (byte)(value >> 16);
    bytes[offset + 4] = (byte)(value >> 24);
    bytes[offset + 3] = (byte)(value >> 32);
    bytes[offset + 2] = (byte)(value >> 40);
    bytes[offset + 1] = (byte)(value >> 48);
    bytes[offset]     = (byte)(value >> 56);
    return 8;
  }

  public static int longTo4ByteArray(byte[] bytes, int offset, long value) {
    bytes[offset + 3] = (byte)value;
    bytes[offset + 2] = (byte)(value >> 8);
    bytes[offset + 1] = (byte)(value >> 16);
    bytes[offset + 0] = (byte)(value >> 24);
    return 4;
  }

  public static int byteArrayToShort(byte[] bytes) {
    return byteArrayToShort(bytes, 0);
  }

  public static int byteArrayToShort(byte[] bytes, int offset) {
    return
        (bytes[offset] & 0xff) << 8 | (bytes[offset + 1] & 0xff);
  }

  // The SSL patented 3-byte Value.
  public static int byteArrayToMedium(byte[] bytes, int offset) {
    return
        (bytes[offset]     & 0xff) << 16 |
            (bytes[offset + 1] & 0xff) << 8  |
            (bytes[offset + 2] & 0xff);
  }

  public static int byteArrayToInt(byte[] bytes) {
    return byteArrayToInt(bytes, 0);
  }

  public static int byteArrayToInt(byte[] bytes, int offset)  {
    return
        (bytes[offset]     & 0xff) << 24 |
            (bytes[offset + 1] & 0xff) << 16 |
            (bytes[offset + 2] & 0xff) << 8  |
            (bytes[offset + 3] & 0xff);
  }

  public static int byteArrayToIntLittleEndian(byte[] bytes, int offset) {
    return
        (bytes[offset + 3] & 0xff) << 24 |
            (bytes[offset + 2] & 0xff) << 16 |
            (bytes[offset + 1] & 0xff) << 8  |
            (bytes[offset]     & 0xff);
  }

  public static long byteArrayToLong(byte[] bytes) {
    return byteArrayToLong(bytes, 0);
  }

  public static long byteArray4ToLong(byte[] bytes, int offset) {
    return
        ((bytes[offset + 0] & 0xffL) << 24) |
            ((bytes[offset + 1] & 0xffL) << 16) |
            ((bytes[offset + 2] & 0xffL) << 8)  |
            ((bytes[offset + 3] & 0xffL));
  }

  public static long byteArray5ToLong(byte[] bytes, int offset) {
    return
        ((bytes[offset]     & 0xffL) << 32) |
        ((bytes[offset + 1] & 0xffL) << 24) |
        ((bytes[offset + 2] & 0xffL) << 16) |
        ((bytes[offset + 3] & 0xffL) << 8) |
        ((bytes[offset + 4] & 0xffL));
  }

  public static long byteArrayToLong(byte[] bytes, int offset) {
    return
        ((bytes[offset]     & 0xffL) << 56) |
            ((bytes[offset + 1] & 0xffL) << 48) |
            ((bytes[offset + 2] & 0xffL) << 40) |
            ((bytes[offset + 3] & 0xffL) << 32) |
            ((bytes[offset + 4] & 0xffL) << 24) |
            ((bytes[offset + 5] & 0xffL) << 16) |
            ((bytes[offset + 6] & 0xffL) << 8)  |
            ((bytes[offset + 7] & 0xffL));
  }

}
