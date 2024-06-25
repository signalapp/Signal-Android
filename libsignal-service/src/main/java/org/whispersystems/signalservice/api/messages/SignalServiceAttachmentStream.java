/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.messages;


import org.whispersystems.signalservice.internal.push.http.CancelationSignal;
import org.whispersystems.signalservice.internal.push.http.ResumableUploadSpec;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

/**
 * Represents a local SignalServiceAttachment to be sent.
 */
public class SignalServiceAttachmentStream extends SignalServiceAttachment implements Closeable {

  private final InputStream                   inputStream;
  private final long                          length;
  private final Optional<String>              fileName;
  private final ProgressListener              listener;
  private final CancelationSignal             cancelationSignal;
  private final Optional<byte[]>              preview;
  private final boolean                       voiceNote;
  private final boolean                       borderless;
  private final boolean                       gif;
  private final boolean                       faststart;
  private final int                           width;
  private final int                           height;
  private final long                          uploadTimestamp;
  private final Optional<String>              caption;
  private final Optional<String>              blurHash;
  private final Optional<ResumableUploadSpec> resumableUploadSpec;
  private final UUID                          uuid;

  public SignalServiceAttachmentStream(InputStream inputStream,
                                       String contentType,
                                       long length,
                                       Optional<String> fileName,
                                       boolean voiceNote,
                                       boolean borderless,
                                       boolean gif,
                                       boolean faststart,
                                       ProgressListener listener,
                                       CancelationSignal cancelationSignal)
  {
    this(inputStream, contentType, length, fileName, voiceNote, borderless, gif, faststart, Optional.empty(), 0, 0, System.currentTimeMillis(), Optional.empty(), Optional.empty(), listener, cancelationSignal, Optional.empty(), UUID.randomUUID());
  }

  public SignalServiceAttachmentStream(InputStream inputStream,
                                       String contentType,
                                       long length,
                                       Optional<String> fileName,
                                       boolean voiceNote,
                                       boolean borderless,
                                       boolean gif,
                                       boolean faststart,
                                       Optional<byte[]> preview,
                                       int width,
                                       int height,
                                       long uploadTimestamp,
                                       Optional<String> caption,
                                       Optional<String> blurHash,
                                       ProgressListener listener,
                                       CancelationSignal cancelationSignal,
                                       Optional<ResumableUploadSpec> resumableUploadSpec,
                                       @Nullable UUID uuid)
  {
    super(contentType);
    this.inputStream         = inputStream;
    this.length              = length;
    this.fileName            = fileName;
    this.listener            = listener;
    this.voiceNote           = voiceNote;
    this.borderless          = borderless;
    this.gif                 = gif;
    this.preview             = preview;
    this.faststart           = faststart;
    this.width               = width;
    this.height              = height;
    this.uploadTimestamp     = uploadTimestamp;
    this.caption             = caption;
    this.blurHash            = blurHash;
    this.cancelationSignal   = cancelationSignal;
    this.resumableUploadSpec = resumableUploadSpec;
    this.uuid                = uuid;
  }

  @Override
  public boolean isStream() {
    return true;
  }

  @Override
  public boolean isPointer() {
    return false;
  }

  public InputStream getInputStream() {
    return inputStream;
  }

  public long getLength() {
    return length;
  }

  public Optional<String> getFileName() {
    return fileName;
  }

  public ProgressListener getListener() {
    return listener;
  }

  public CancelationSignal getCancelationSignal() {
    return cancelationSignal;
  }

  public Optional<byte[]> getPreview() {
    return preview;
  }

  public boolean getVoiceNote() {
    return voiceNote;
  }

  public boolean isBorderless() {
    return borderless;
  }

  public boolean isGif() {
    return gif;
  }

  public boolean isFaststart() {
    return faststart;
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }

  public Optional<String> getCaption() {
    return caption;
  }

  public Optional<String> getBlurHash() {
    return blurHash;
  }

  public long getUploadTimestamp() {
    return uploadTimestamp;
  }

  public Optional<ResumableUploadSpec> getResumableUploadSpec() {
    return resumableUploadSpec;
  }

  public @Nullable UUID getUuid() {
    return uuid;
  }

  @Override
  public void close() throws IOException {
    inputStream.close();
  }
}
