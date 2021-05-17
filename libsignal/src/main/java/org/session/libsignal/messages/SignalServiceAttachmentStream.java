/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.session.libsignal.messages;

import org.session.libsignal.utilities.guava.Optional;

import java.io.InputStream;

/**
 * Represents a local SignalServiceAttachment to be sent.
 */
public class SignalServiceAttachmentStream extends SignalServiceAttachment {

  private final InputStream      inputStream;
  private final long             length;
  private final Optional<String> fileName;
  private final ProgressListener listener;
  private final Optional<byte[]> preview;
  private final boolean          voiceNote;
  private final int              width;
  private final int              height;
  private final Optional<String> caption;

  public SignalServiceAttachmentStream(InputStream inputStream, String contentType, long length, Optional<String> fileName, boolean voiceNote, Optional<byte[]> preview, int width, int height, Optional<String> caption, ProgressListener listener) {
    super(contentType);
    this.inputStream = inputStream;
    this.length      = length;
    this.fileName    = fileName;
    this.listener    = listener;
    this.voiceNote   = voiceNote;
    this.preview     = preview;
    this.width       = width;
    this.height      = height;
    this.caption     = caption;
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

  public Optional<byte[]> getPreview() {
    return preview;
  }

  public boolean getVoiceNote() {
    return voiceNote;
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
}
