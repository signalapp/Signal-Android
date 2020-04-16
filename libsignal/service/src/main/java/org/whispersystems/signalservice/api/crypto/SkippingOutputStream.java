package org.whispersystems.signalservice.api.crypto;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * SkippingOutputStream will skip a number of bytes being written as specified by toSkip and then
 * continue writing all remaining bytes to the wrapped output stream.
 */
public class SkippingOutputStream extends FilterOutputStream {

  private long toSkip;

  public SkippingOutputStream(long toSkip, OutputStream wrapped) {
    super(wrapped);
    this.toSkip  = toSkip;
  }

  public void write(int b) throws IOException {
    if (toSkip > 0) {
      toSkip--;
    } else {
      out.write(b);
    }
  }

  public void write(byte[] b) throws IOException {
    write(b, 0, b.length);
  }

  public void write(byte[] b, int off, int len) throws IOException {
    if (b == null) {
      throw new NullPointerException();
    }

    if (off < 0 || off > b.length || len < 0 || len + off > b.length || len + off < 0) {
      throw new IndexOutOfBoundsException();
    }

    if (toSkip > 0) {
      if (len <= toSkip) {
        toSkip -= len;
      } else {
        out.write(b, off + (int) toSkip, len - (int) toSkip);
        toSkip = 0;
      }
    } else {
      out.write(b, off, len);
    }
  }
}
