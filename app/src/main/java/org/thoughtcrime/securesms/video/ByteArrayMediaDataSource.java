package org.thoughtcrime.securesms.video;

import android.media.MediaDataSource;

import java.io.IOException;

public class ByteArrayMediaDataSource extends MediaDataSource {

  private byte[] data;

  public ByteArrayMediaDataSource(byte[] data) {
    this.data = data;
  }

  @Override
  public int readAt(long position, byte[] buffer, int offset, int size) throws IOException {
    if (data == null) throw new IOException("ByteArrayMediaDataSource is closed");

    long bytesAvailable = getSize() - position;
    int  read           = Math.min(size, (int) bytesAvailable);
    if (read <= 0) return -1;

    if (buffer != null) {
      System.arraycopy(data, (int) position, buffer, offset, read);
    }

    return read;
  }

  @Override
  public long getSize() throws IOException {
    if (data == null) throw new IOException("ByteArrayMediaDataSource is closed");
    return data.length;
  }

  @Override
  public void close() throws IOException {
    data = null;
  }
}
