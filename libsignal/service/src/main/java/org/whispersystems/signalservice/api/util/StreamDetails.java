package org.whispersystems.signalservice.api.util;

import org.whispersystems.libsignal.logging.Log;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

public final class StreamDetails implements Closeable {

  private static final String TAG = StreamDetails.class.getSimpleName();

  private final InputStream stream;
  private final String      contentType;
  private final long        length;

  public StreamDetails(InputStream stream, String contentType, long length) {
    this.stream      = stream;
    this.contentType = contentType;
    this.length      = length;
  }

  public InputStream getStream() {
    return stream;
  }

  public String getContentType() {
    return contentType;
  }

  public long getLength() {
    return length;
  }

  @Override
  public void close() {
    try {
      stream.close();
    } catch (IOException e) {
      Log.w(TAG, e);
    }
  }
}
