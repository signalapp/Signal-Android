package org.thoughtcrime.securesms.backup;

import java.io.IOException;
import java.io.OutputStream;

class DecodeBase64Writer {

  private final static byte OFF_0 = 0;
  private final static byte OFF_6 = 1;
  private final static byte OFF_4 = 2;
  private final static byte OFF_2 = 3;
  private final static byte EQ_OFF_2 = 4;
  private final static byte F = 5;

  private static final byte IGNORE_CHARACTER = -1;
  private static final byte EQUAL_SIGN_READ = -2;

  private byte buffer;
  private byte state;
  private OutputStream outputStream;

  DecodeBase64Writer(OutputStream outputStream) {
    this.buffer = 0;
    this.state = OFF_0;
    this.outputStream = outputStream;
  }

  public void write(char[] chars)
          throws IOException {
    write(chars, 0, chars.length);
  }

  public void write(char[] chars, int offset, int length)
          throws IOException {
    for (int i = 0; i < length; ++i) {
      write(chars[offset + i]);
    }
  }

  public void write(char c)
          throws IOException {
    byte b = charToNum(c);

    if ((b == IGNORE_CHARACTER) && (state != F)) {
      return;
    }

    switch (state) {
      case OFF_0:
        if (b == EQUAL_SIGN_READ) {
          throw new RuntimeException("unexpected =");
        }
        buffer = (byte) (b << 2);
        state = OFF_6;
        break;
      case OFF_6:
        if (b == EQUAL_SIGN_READ) {
          throw new RuntimeException("unexpected =");
        }
        outputStream.write(buffer | (b >>> 4));
        buffer = (byte) ((b & 15) << 4);
        state = OFF_4;
        break;
      case OFF_4:
        if (b == EQUAL_SIGN_READ) {
          state = EQ_OFF_2;
        } else {
          outputStream.write(buffer | (b >>> 2));
          buffer = (byte) ((b & 3) << 6);
          state = OFF_2;
        }
        break;
      case OFF_2:
        if (b == EQUAL_SIGN_READ) {
          state = F;
        } else {
          outputStream.write(buffer | b);
          buffer = 0;
          state = OFF_0;
        }
        break;
      case EQ_OFF_2:
        if (b == EQUAL_SIGN_READ) {
          state = F;
        } else {
          throw new RuntimeException("expected =, but got " + c);
        }
        break;
      default:
        throw new RuntimeException("unreachable statement");
    }
  }

  private byte charToNum(char c) {
    if ((c < 0) || (c > 127)) {
      throw new RuntimeException("no valid char");
    }
    if ((c >= 'A') && (c <= 'Z')) {
      return (byte) (c - 'A');
    }
    if ((c >= 'a') && (c <= 'z')) {
      return (byte) (c - 'a' + 26);
    }
    if ((c >= '0') && (c <= '9')) {
      return (byte) (c - '0' + 52);
    }
    if (c == '+') {
      return 62;
    }
    if (c == '/') {
      return 63;
    }
    if (c == '=') {
      return EQUAL_SIGN_READ;
    }
    // else return special value "please ignore"
    return IGNORE_CHARACTER;
  }

  public void close()
          throws IOException {
    if (outputStream != null) {
      try {
        outputStream.close();
      } catch (IOException e) {
        outputStream = null;
        throw e;
      }
    }
    outputStream = null;
  }

}
