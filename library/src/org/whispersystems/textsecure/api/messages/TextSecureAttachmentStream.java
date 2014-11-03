package org.whispersystems.textsecure.api.messages;

import java.io.InputStream;

public class TextSecureAttachmentStream extends TextSecureAttachment {

  private final InputStream inputStream;
  private final long        length;

  public TextSecureAttachmentStream(InputStream inputStream, String contentType, long length) {
    super(contentType);
    this.inputStream = inputStream;
    this.length      = length;
  }

  @Override
  public boolean isStream() {
    return false;
  }

  @Override
  public boolean isPointer() {
    return true;
  }

  public InputStream getInputStream() {
    return inputStream;
  }

  public long getLength() {
    return length;
  }
}
