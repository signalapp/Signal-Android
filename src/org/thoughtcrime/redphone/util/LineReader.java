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

import android.util.Log;

import org.thoughtcrime.securesms.util.Hex;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;

/**
 *
 * Reads lines off an InputStream.  Seems like they have BufferedReaders
 * for that, but I'm sure I had a reason to write my own.
 *
 * @author Moxie Marlinspike
 *
 */
public class LineReader {

  private final InputStream in;
  private ByteArrayOutputStream baos;

  public LineReader(InputStream in) {
    this.in = in;
    this.baos = new ByteArrayOutputStream();
  }

  private byte[] chompLine(byte[] buffer, int newlineIndex, int bufferLength) {
    byte[] line = new byte[newlineIndex];
    System.arraycopy(buffer, 0, line, 0, newlineIndex);

    Log.w("LineReader", "Chomped line: " + Hex.toString(line));
    Log.w("LineReader", "Buffer length: " + bufferLength);

    if ((newlineIndex+2) < bufferLength) {
      Log.w("LineReader", "Writing remaining to buffer, offset: " + (newlineIndex + 2) +
          " Length: " + (bufferLength - (newlineIndex + 2)));
      baos.write(buffer, newlineIndex+2, bufferLength-(newlineIndex+2));
    }

    return line;
  }

  private int findNewline(byte[] buffer, int offset, int length) {
    for (int i=offset;i<length;i++) {
      if (buffer[i] == (byte)0x0A && i>0 && buffer[i-1] == (byte)0x0D)
        return i-1;
    }

    return -1;
  }

  public boolean waitForAvailable() throws IOException {
    try {
      byte[] buffer = new byte[500];
      int read      = in.read(buffer);

      if (read <= 0)
        return false;

      baos.write(buffer, 0, read);
      return true;
    } catch (InterruptedIOException iie) {
      return false;
    }
  }

  public String readLine() throws IOException {
    byte[] buffer = new byte[4096];
    int read      = 0;

    do {
      baos.write(buffer, 0, read);
      byte[] bufferedBytes = baos.toByteArray();
      int newlineIndex     = 0;

      if ((newlineIndex = findNewline(bufferedBytes, 0, bufferedBytes.length)) != -1) {
        baos.reset();
        return new String(chompLine(bufferedBytes, newlineIndex, bufferedBytes.length), "UTF8");
      }
    } while ((read = in.read(buffer)) != -1);

    throw new IOException("Stream closed before newline found...");
  }

  public byte[] readFully(int size) throws IOException {
    byte[] buffer = new byte[size];
    int remaining = size;

    if (baos.size() > 0) {
      byte[] bufferedBytes = baos.toByteArray();
      int toCopy           = Math.min(size, bufferedBytes.length);
      remaining           -= toCopy;

      System.arraycopy(bufferedBytes, 0, buffer, 0, toCopy);

      baos.reset();
      baos.write(bufferedBytes, toCopy, bufferedBytes.length-toCopy);
    }

    while (remaining > 0) {
      int read   = in.read(buffer, size-remaining, remaining);

      if (read == -1)
        throw new IOException("Socket closed before buffer filled...");

      remaining -= read;
    }

    return buffer;
  }

}

