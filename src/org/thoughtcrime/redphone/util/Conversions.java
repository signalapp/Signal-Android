/*
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.thoughtcrime.redphone.util;

import java.math.BigInteger;

/**
 * Utility class for doing conversions between various primitive
 * types. (Mostly intergal types to and from byte arrays).
 *
 * @author Moxie Marlinspike
 */
public class Conversions {

  public static byte[] combine(byte[] first, byte[] second) {
    byte[] combined = new byte[first.length + second.length];

    System.arraycopy(first, 0, combined, 0, first.length);
    System.arraycopy(second, 0, combined, first.length, second.length);

    return combined;
  }

  public static void bigIntegerToByteArray(byte[] bytes, BigInteger value) {
    BigInteger mask = BigInteger.valueOf(0xFF);

    for (int i=bytes.length-1;i>=0;i--) {
    bytes[i] = (byte)value.shiftRight((bytes.length-1-i) * 8).and(mask).intValue();
    }
  }

  public static BigInteger byteArrayToBigInteger(byte[] bytes) {
    BigInteger value = BigInteger.valueOf(bytes[0]).and(BigInteger.valueOf(0xFF));

    for (int i=1;i<bytes.length;i++) {
      value = value.shiftLeft(8);
      value = value.or(BigInteger.valueOf(bytes[i]).and(BigInteger.valueOf(0xFF)) );
    }

    return value;
  }

  public static void stringToByteArray(byte[] bytes, int offset, String value) {
    for (int i=0,x=value.length();i<x;i++) {
      char index = value.charAt(i);
      bytes[offset+i] = (byte)index;
    }
  }

  public static byte[] stringToByteArray(String value) {
    byte[] results = new byte[value.length()];
    stringToByteArray(results, 0, value);
    return results;
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

   public static byte setBit( byte b, int offset, boolean bit ) {
    if( bit )
      return (byte) (b | ( 1 << offset ));
    else
      return (byte) (b & ( 0xFF ^ (1 << offset) ) );
  }

  public static boolean getBit( byte b, int offset ) {
    if ( (( b >> offset ) & 0x01) != 0)
      return true;
    else
      return false;
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

  public static long byteArray4ToLong( byte[] bytes, int offset) {
      return
        ((bytes[offset + 0] & 0xffL) << 24) |
        ((bytes[offset + 1] & 0xffL) << 16) |
        ((bytes[offset + 2] & 0xffL) << 8)  |
        ((bytes[offset + 3] & 0xffL));
  }

  public static void shortsToBytes( short[] shorts, byte[] bytes, int numShorts ) {
    for( int i=0; i < numShorts; i++ ) {
      shortToByteArray(bytes, i*2, shorts[i] );
    }
  }

  public static void bytesToShorts( byte[] bytes, short[] shorts, int numShorts ) {
    for( int i=0; i < numShorts; i++ ) {
      shorts[i] = (short) byteArrayToShort( bytes, i*2 );
    }
  }

  public static int longTo4ByteArray(byte[] bytes, int offset, long value) {
      bytes[offset + 3] = (byte)value;
      bytes[offset + 2] = (byte)(value >> 8);
      bytes[offset + 1] = (byte)(value >> 16);
      bytes[offset + 0] = (byte)(value >> 24);
      return 4;
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

  public static int byteArrayToShort(byte[] bytes) {
    return byteArrayToShort(bytes, 0);
  }

  public static short byteArrayToShort(byte[] bytes, int offset) {
    return
      (short) ((bytes[offset] & 0xff) << 8 | (bytes[offset + 1] & 0xff));
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

  public static long byteArrayToLong(byte[] bytes) {
    return byteArrayToLong(bytes, 0);
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

