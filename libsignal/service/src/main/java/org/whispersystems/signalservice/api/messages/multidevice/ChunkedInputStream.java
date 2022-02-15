package org.whispersystems.signalservice.api.messages.multidevice;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class ChunkedInputStream {

  protected final InputStream in;

  public ChunkedInputStream(InputStream in) {
    this.in = in;
  }

  long readRawVarint32() throws IOException {
    long result = 0;
    for (int shift = 0; shift < 32; shift += 7) {
      int tmpInt = in.read();
      if (tmpInt < 0) {
        return -1;
      }
      final byte b = (byte) tmpInt;
      result |= (long) (b & 0x7F) << shift;
      if ((b & 0x80) == 0) {
        return result;
      }
    }
    throw new IOException("Malformed varint!");
  }

  protected static final class LimitedInputStream extends InputStream {

    private final InputStream in;

    private long left;
    private long mark = -1;

    LimitedInputStream(InputStream in, long limit) {
      this.in   = in;
      this.left = limit;
    }

    @Override
    public int available() throws IOException {
      return (int) Math.min(in.available(), left);
    }

    // it's okay to mark even if mark isn't supported, as reset won't work
    @Override
    public synchronized void mark(int readLimit) {
      in.mark(readLimit);
      mark = left;
    }

    @Override
    public int read() throws IOException {
      if (left == 0) {
        return -1;
      }

      int result = in.read();
      if (result != -1) {
        --left;
      }
      return result;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      if (left == 0) {
        return -1;
      }

      len = (int) Math.min(len, left);
      int result = in.read(b, off, len);
      if (result != -1) {
        left -= result;
      }
      return result;
    }

    @Override
    public synchronized void reset() throws IOException {
      if (!in.markSupported()) {
        throw new IOException("Mark not supported");
      }
      if (mark == -1) {
        throw new IOException("Mark not set");
      }

      in.reset();
      left = mark;
    }

    @Override
    public long skip(long n) throws IOException {
      n = Math.min(n, left);
      long skipped = in.skip(n);
      left -= skipped;
      return skipped;
    }

    @Override
    public void close() throws IOException {
      // do nothing
    }
  }

}
