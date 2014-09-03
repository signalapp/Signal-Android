package org.thoughtcrime.securesms.util;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Memory-friendly workaround found from https://code.google.com/p/android/issues/detail?id=6066#c23
 * to solve decoding problems in bitmaps from InputStreams that don't skip if no more stream is available.
 */
class FlushedInputStream extends FilterInputStream {
  public FlushedInputStream(InputStream inputStream) {
    super(inputStream);
  }

  @Override
  public long skip(long n) throws IOException {
    long totalBytesSkipped = 0L;
    while (totalBytesSkipped < n) {
      long bytesSkipped = in.skip(n - totalBytesSkipped);
      if (bytesSkipped == 0L) {
        int inByte = read();
        if (inByte < 0) {
          break;
        } else {
          bytesSkipped = 1;
        }
      }
      totalBytesSkipped += bytesSkipped;
    }
    return totalBytesSkipped;
  }
}
