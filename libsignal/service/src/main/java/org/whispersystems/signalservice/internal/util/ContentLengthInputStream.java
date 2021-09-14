package org.whispersystems.signalservice.internal.util;


import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class ContentLengthInputStream extends FilterInputStream {

  private long bytesRemaining;

  public ContentLengthInputStream(InputStream inputStream, long contentLength) {
    super(inputStream);
    this.bytesRemaining = contentLength;
  }

  @Override
  public int read() throws IOException {
    if (bytesRemaining == 0) return -1;
    int result = super.read();
    bytesRemaining--;

    return result;
  }

  @Override
  public int read(byte[] buffer) throws IOException {
    return read(buffer, 0, buffer.length);
  }

  @Override
  public int read(byte[] buffer, int offset, int length) throws IOException {
    if (bytesRemaining == 0) return -1;

    int lengthToRead = Math.min(length, Util.toIntExact(bytesRemaining));
    int result       = super.read(buffer, offset, lengthToRead);

    if (result <= lengthToRead) {
      bytesRemaining -= result;
      return result;
    } else {
      bytesRemaining = 0;
      return lengthToRead;
    }
  }
}