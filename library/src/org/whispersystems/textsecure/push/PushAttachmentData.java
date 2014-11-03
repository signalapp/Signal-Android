package org.whispersystems.textsecure.push;

import java.io.InputStream;

public class PushAttachmentData {

  private final String      contentType;
  private final InputStream data;
  private final long        dataSize;
  private final byte[]      key;

  public PushAttachmentData(String contentType, InputStream data, long dataSize, byte[] key) {
    this.contentType = contentType;
    this.data        = data;
    this.dataSize    = dataSize;
    this.key         = key;
  }

  public String getContentType() {
    return contentType;
  }

  public InputStream getData() {
    return data;
  }

  public long getDataSize() {
    return dataSize;
  }

  public byte[] getKey() {
    return key;
  }
}
