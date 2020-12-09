package org.signal.core.util;

import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Utility methods for input and output streams.
 */
public final class StreamUtil {

  private static final String TAG = Log.tag(StreamUtil.class);

  private StreamUtil() {}

  public static void close(@Nullable Closeable closeable) {
    if (closeable == null) return;

    try {
      closeable.close();
    } catch (IOException e) {
      Log.w(TAG, e);
    }
  }

  public static long getStreamLength(InputStream in) throws IOException {
    byte[] buffer    = new byte[4096];
    int    totalSize = 0;

    int read;

    while ((read = in.read(buffer)) != -1) {
      totalSize += read;
    }

    return totalSize;
  }

  public static void readFully(InputStream in, byte[] buffer) throws IOException {
    readFully(in, buffer, buffer.length);
  }

  public static void readFully(InputStream in, byte[] buffer, int len) throws IOException {
    int offset = 0;

    for (;;) {
      int read = in.read(buffer, offset, len - offset);
      if (read == -1) throw new EOFException("Stream ended early");

      if (read + offset < len) offset += read;
      else                		 return;
    }
  }

  public static byte[] readFully(InputStream in) throws IOException {
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    byte[] buffer              = new byte[4096];
    int read;

    while ((read = in.read(buffer)) != -1) {
      bout.write(buffer, 0, read);
    }

    in.close();

    return bout.toByteArray();
  }

  public static String readFullyAsString(InputStream in) throws IOException {
    return new String(readFully(in));
  }

  public static long copy(InputStream in, OutputStream out) throws IOException {
    byte[] buffer = new byte[64 * 1024];
    int read;
    long total = 0;

    while ((read = in.read(buffer)) != -1) {
      out.write(buffer, 0, read);
      total += read;
    }

    in.close();
    out.close();

    return total;
  }
}
