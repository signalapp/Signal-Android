package org.whispersystems.signalservice.internal.push;


import org.whispersystems.signalservice.internal.push.http.OutputStreamFactory;

import java.io.InputStream;

public class ProfileAvatarData {

  private final InputStream         data;
  private final long                dataLength;
  private final String              contentType;
  private final OutputStreamFactory outputStreamFactory;

  public ProfileAvatarData(InputStream data, long dataLength, String contentType, OutputStreamFactory outputStreamFactory) {
    this.data                = data;
    this.dataLength          = dataLength;
    this.contentType         = contentType;
    this.outputStreamFactory = outputStreamFactory;
  }

  public InputStream getData() {
    return data;
  }

  public long getDataLength() {
    return dataLength;
  }

  public OutputStreamFactory getOutputStreamFactory() {
    return outputStreamFactory;
  }

  public String getContentType() {
    return contentType;
  }
}
