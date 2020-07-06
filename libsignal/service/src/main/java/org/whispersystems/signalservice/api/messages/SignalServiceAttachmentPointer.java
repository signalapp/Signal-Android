/*
 * Copyright (C) 2014-2017 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.messages;

import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;

/**
 * Represents a received SignalServiceAttachment "handle."  This
 * is a pointer to the actual attachment content, which needs to be
 * retrieved using {@link SignalServiceMessageReceiver#retrieveAttachment(SignalServiceAttachmentPointer, java.io.File, long)}
 *
 * @author Moxie Marlinspike
 */
public class SignalServiceAttachmentPointer extends SignalServiceAttachment {

  private final int                             cdnNumber;
  private final SignalServiceAttachmentRemoteId remoteId;
  private final byte[]                          key;
  private final Optional<Integer>               size;
  private final Optional<byte[]>                preview;
  private final Optional<byte[]>                digest;
  private final Optional<String>                fileName;
  private final boolean                         voiceNote;
  private final boolean                         borderless;
  private final int                             width;
  private final int                             height;
  private final Optional<String>                caption;
  private final Optional<String>                blurHash;
  private final long                            uploadTimestamp;

  public SignalServiceAttachmentPointer(int cdnNumber,
                                        SignalServiceAttachmentRemoteId remoteId,
                                        String contentType,
                                        byte[] key,
                                        Optional<Integer> size,
                                        Optional<byte[]> preview,
                                        int width,
                                        int height,
                                        Optional<byte[]> digest,
                                        Optional<String> fileName,
                                        boolean voiceNote,
                                        boolean borderless,
                                        Optional<String> caption,
                                        Optional<String> blurHash,
                                        long uploadTimestamp)
  {
    super(contentType);
    this.cdnNumber       = cdnNumber;
    this.remoteId        = remoteId;
    this.key             = key;
    this.size            = size;
    this.preview         = preview;
    this.width           = width;
    this.height          = height;
    this.digest          = digest;
    this.fileName        = fileName;
    this.voiceNote       = voiceNote;
    this.borderless      = borderless;
    this.caption         = caption;
    this.blurHash        = blurHash;
    this.uploadTimestamp = uploadTimestamp;
  }

  public int getCdnNumber() {
    return cdnNumber;
  }

  public SignalServiceAttachmentRemoteId getRemoteId() {
    return remoteId;
  }

  public byte[] getKey() {
    return key;
  }

  @Override
  public boolean isStream() {
    return false;
  }

  @Override
  public boolean isPointer() {
    return true;
  }

  public Optional<Integer> getSize() {
    return size;
  }

  public Optional<String> getFileName() {
    return fileName;
  }

  public Optional<byte[]> getPreview() {
    return preview;
  }

  public Optional<byte[]> getDigest() {
    return digest;
  }

  public boolean getVoiceNote() {
    return voiceNote;
  }

  public boolean isBorderless() {
    return borderless;
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
}
