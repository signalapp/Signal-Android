package org.whispersystems.textsecure.push;

public class PushAttachmentData {

  private final String contentType;
  private final byte[] data;

  public PushAttachmentData(String contentType, byte[] data) {
    this.contentType = contentType;
    this.data        = data;
  }

  public String getContentType() {
    return contentType;
  }

  public byte[] getData() {
    return data;
  }

}
