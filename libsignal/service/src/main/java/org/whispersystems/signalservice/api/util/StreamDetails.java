package org.whispersystems.signalservice.api.util;


import java.io.InputStream;

public class StreamDetails {

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
}
