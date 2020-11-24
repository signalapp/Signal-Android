/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.internal.util;

import java.io.IOException;

/**
 * Utility for generating hex dumps.
 */
public class Hex {

  private final static int HEX_DIGITS_START = 10;
  private final static int ASCII_TEXT_START = HEX_DIGITS_START + (16*2 + (16/2));

  final static String EOL = System.getProperty("line.separator");

  private final static char[] HEX_DIGITS = {
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
  };

  public static String toString(byte[] bytes) {
    return toString(bytes, 0, bytes.length);
  }

  public static String toString(byte[] bytes, int offset, int length) {
    StringBuffer buf = new StringBuffer();
    for (int i = 0; i < length; i++) {
      appendHexChar(buf, bytes[offset + i]);
      buf.append(' ');
    }
    return buf.toString();
  }

  public static String toStringCondensed(byte[] bytes) {
    StringBuffer buf = new StringBuffer();
    for (int i=0;i<bytes.length;i++) {
      appendHexChar(buf, bytes[i]);
    }
    return buf.toString();
  }

  public static byte[] fromStringCondensed(String encoded) throws IOException {
    final char[] data = encoded.toCharArray();
    final int    len  = data.length;

    if ((len & 0x01) != 0) {
      throw new IOException("Odd number of characters.");
    }

    final byte[] out = new byte[len >> 1];

    // two characters form the hex value.
    for (int i = 0, j = 0; j < len; i++) {
      int f = Character.digit(data[j], 16) << 4;
      j++;
      f = f | Character.digit(data[j], 16);
      j++;
      out[i] = (byte) (f & 0xFF);
    }

    return out;
  }

  public static String dump(byte[] bytes) {
    return dump(bytes, 0, bytes.length);
  }

  public static String dump(byte[] bytes, int offset, int length) {
    StringBuffer buf = new StringBuffer();
    int lines = ((length - 1) / 16) + 1;
    int lineOffset;
    int lineLength;

    for (int i = 0; i < lines; i++) {
      lineOffset = (i * 16) + offset;
      lineLength = Math.min(16, (length - (i * 16)));
      appendDumpLine(buf, i, bytes, lineOffset, lineLength);
      buf.append(EOL);
    }

    return buf.toString();
  }

  private static void appendDumpLine(StringBuffer buf, int line, byte[] bytes, int lineOffset, int lineLength) {
    buf.append(HEX_DIGITS[(line >> 28) & 0xf]);
    buf.append(HEX_DIGITS[(line >> 24) & 0xf]);
    buf.append(HEX_DIGITS[(line >> 20) & 0xf]);
    buf.append(HEX_DIGITS[(line >> 16) & 0xf]);
    buf.append(HEX_DIGITS[(line >> 12) & 0xf]);
    buf.append(HEX_DIGITS[(line >>  8) & 0xf]);
    buf.append(HEX_DIGITS[(line >>  4) & 0xf]);
    buf.append(HEX_DIGITS[(line      ) & 0xf]);
    buf.append(": ");

    for (int i = 0; i < 16; i++) {
      int idx = i + lineOffset;
      if (i < lineLength) {
        int b = bytes[idx];
        appendHexChar(buf, b);
      } else {
        buf.append("  ");
      }
      if ((i % 2) == 1) {
        buf.append(' ');
      }
    }

    for (int i = 0; i < 16 && i < lineLength; i++) {
      int idx = i + lineOffset;
      int b = bytes[idx];
      if (b >= 0x20 && b <= 0x7e) {
        buf.append((char)b);
      } else {
        buf.append('.');
      }
    }
  }

  private static void appendHexChar(StringBuffer buf, int b) {
    buf.append(HEX_DIGITS[(b >> 4) & 0xf]);
    buf.append(HEX_DIGITS[b & 0xf]);
  }

}
