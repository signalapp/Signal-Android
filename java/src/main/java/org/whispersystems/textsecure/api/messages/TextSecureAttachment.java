/**
 * Copyright (C) 2014 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
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

  public static Builder newStreamBuilder() {
    return new Builder();
  }

  public static class Builder {

    private InputStream inputStream;
    private String      contentType;
    private long        length;

    private Builder() {}

    public Builder withStream(InputStream inputStream) {
      this.inputStream = inputStream;
      return this;
    }

    public Builder withContentType(String contentType) {
      this.contentType = contentType;
      return this;
    }

    public Builder withLength(long length) {
      this.length = length;
      return this;
    }

    public TextSecureAttachmentStream build() {
      if (inputStream == null) throw new IllegalArgumentException("Must specify stream!");
      if (contentType == null) throw new IllegalArgumentException("No content type specified!");
      if (length == 0)         throw new IllegalArgumentException("No length specified!");

      return new TextSecureAttachmentStream(inputStream, contentType, length);
    }
  }
}
