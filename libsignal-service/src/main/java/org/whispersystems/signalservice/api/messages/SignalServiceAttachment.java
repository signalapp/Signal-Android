/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.messages;


import org.whispersystems.signalservice.internal.push.http.CancelationSignal;
import org.whispersystems.signalservice.internal.push.http.ResumableUploadSpec;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

public abstract class SignalServiceAttachment {

  private final String contentType;

  protected SignalServiceAttachment(String contentType) {
    this.contentType = contentType;
  }

  public String getContentType() {
    return contentType;
  }

  public abstract boolean isStream();
  public abstract boolean isPointer();

  public SignalServiceAttachmentStream asStream() {
    return (SignalServiceAttachmentStream)this;
  }

  public SignalServiceAttachmentPointer asPointer() {
    return (SignalServiceAttachmentPointer)this;
  }

  public static Builder newStreamBuilder() {
    return new Builder();
  }

  public static class Builder {

    private InputStream             inputStream;
    private String                  contentType;
    private String                  fileName;
    private long                    length;
    private ProgressListener        listener;
    private CancelationSignal       cancelationSignal;
    private boolean                 voiceNote;
    private boolean                 borderless;
    private boolean                 gif;
    private boolean                 faststart;
    private int                     width;
    private int                     height;
    private String                  caption;
    private String                  blurHash;
    private long                    uploadTimestamp;
    private ResumableUploadSpec     resumableUploadSpec;
    private UUID                    uuid;

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

    public Builder withFileName(String fileName) {
      this.fileName = fileName;
      return this;
    }

    public Builder withListener(ProgressListener listener) {
      this.listener = listener;
      return this;
    }

    public Builder withCancelationSignal(CancelationSignal cancelationSignal) {
      this.cancelationSignal = cancelationSignal;
      return this;
    }

    public Builder withVoiceNote(boolean voiceNote) {
      this.voiceNote = voiceNote;
      return this;
    }

    public Builder withBorderless(boolean borderless) {
      this.borderless = borderless;
      return this;
    }

    public Builder withGif(boolean gif) {
      this.gif = gif;
      return this;
    }

    public Builder withFaststart(boolean faststart) {
      this.faststart = faststart;
      return this;
    }

    public Builder withWidth(int width) {
      this.width = width;
      return this;
    }

    public Builder withHeight(int height) {
      this.height = height;
      return this;
    }

    public Builder withCaption(String caption) {
      this.caption = caption;
      return this;
    }

    public Builder withBlurHash(String blurHash) {
      this.blurHash = blurHash;
      return this;
    }

    public Builder withUploadTimestamp(long uploadTimestamp) {
      this.uploadTimestamp = uploadTimestamp;
      return this;
    }

    public Builder withResumableUploadSpec(ResumableUploadSpec resumableUploadSpec) {
      this.resumableUploadSpec = resumableUploadSpec;
      return this;
    }

    public Builder withUuid(@Nullable UUID uuid) {
      this.uuid = uuid;
      return this;
    }

    public SignalServiceAttachmentStream build() {
      if (inputStream == null) throw new IllegalArgumentException("Must specify stream!");
      if (contentType == null) throw new IllegalArgumentException("No content type specified!");
      if (length == 0)         throw new IllegalArgumentException("No length specified!");

      return new SignalServiceAttachmentStream(inputStream,
                                               contentType,
                                               length,
                                               Optional.ofNullable(fileName),
                                               voiceNote,
                                               borderless,
                                               gif,
                                               faststart,
                                               Optional.empty(),
                                               width,
                                               height,
                                               uploadTimestamp,
                                               Optional.ofNullable(caption),
                                               Optional.ofNullable(blurHash),
                                               listener,
                                               cancelationSignal,
                                               Optional.ofNullable(resumableUploadSpec),
                                               uuid);
    }
  }

  /**
   * An interface to receive progress information on upload/download of
   * an attachment.
   */
  public interface ProgressListener {
    /**
     * Called on a progress change event.
     *
     * @param total    The total amount to transmit/receive in bytes.
     * @param progress The amount that has been transmitted/received in bytes thus far
     */
    void onAttachmentProgress(long total, long progress);

    boolean shouldCancel();
  }
}
