package org.whispersystems.textsecure.api.messages;

import java.io.InputStream;

public abstract class TextSecureAttachment {

  private final String contentType;

  protected TextSecureAttachment(String contentType) {
    this.contentType = contentType;
  }

  public String getContentType() {
    return contentType;
  }

  public abstract boolean isStream();
  public abstract boolean isPointer();

  public TextSecureAttachmentStream asStream() {
    return (TextSecureAttachmentStream)this;
  }

  public TextSecureAttachmentPointer asPointer() {
    return (TextSecureAttachmentPointer)this;
  }
}
