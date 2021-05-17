/*
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.session.libsignal.messages;

import org.session.libsignal.utilities.guava.Optional;
import org.session.libsignal.protos.SignalServiceProtos;

public class SignalServiceContent {
  private final String  sender;
  private final int     senderDevice;
  private final long    timestamp;
  private final boolean needsReceipt;

  // Loki
  private Optional<SignalServiceDataMessage>          message;
  private final Optional<SignalServiceReceiptMessage> readMessage;
  private final Optional<SignalServiceTypingMessage>  typingMessage;

  // Loki
  public Optional<SignalServiceProtos.Content> configurationMessageProto = Optional.absent();
  public Optional<String>                      senderDisplayName         = Optional.absent();
  public Optional<String>                      senderProfilePictureURL   = Optional.absent();

  public SignalServiceContent(SignalServiceDataMessage message, String sender, int senderDevice, long timestamp, boolean needsReceipt) {
    this.sender                   = sender;
    this.senderDevice             = senderDevice;
    this.timestamp                = timestamp;
    this.needsReceipt             = needsReceipt;
    this.message                  = Optional.fromNullable(message);
    this.readMessage              = Optional.absent();
    this.typingMessage            = Optional.absent();
  }

  public SignalServiceContent(SignalServiceReceiptMessage receiptMessage, String sender, int senderDevice, long timestamp) {
    this.sender                   = sender;
    this.senderDevice             = senderDevice;
    this.timestamp                = timestamp;
    this.needsReceipt             = false;
    this.message                  = Optional.absent();
    this.readMessage              = Optional.of(receiptMessage);
    this.typingMessage            = Optional.absent();
  }

  public SignalServiceContent(SignalServiceTypingMessage typingMessage, String sender, int senderDevice, long timestamp) {
    this.sender                   = sender;
    this.senderDevice             = senderDevice;
    this.timestamp                = timestamp;
    this.needsReceipt             = false;
    this.message                  = Optional.absent();
    this.readMessage              = Optional.absent();
    this.typingMessage            = Optional.of(typingMessage);
  }

  public SignalServiceContent(SignalServiceProtos.Content configurationMessageProto, String sender, int senderDevice, long timestamp) {
    this.sender                    = sender;
    this.senderDevice              = senderDevice;
    this.timestamp                 = timestamp;
    this.needsReceipt              = false;
    this.message                   = Optional.absent();
    this.readMessage               = Optional.absent();
    this.typingMessage             = Optional.absent();
    this.configurationMessageProto = Optional.fromNullable(configurationMessageProto);
  }

  public Optional<SignalServiceDataMessage> getDataMessage() {
    return message;
  }

  public void setDataMessage(SignalServiceDataMessage message) { this.message = Optional.fromNullable(message); }

  public Optional<SignalServiceReceiptMessage> getReceiptMessage() {
    return readMessage;
  }

  public Optional<SignalServiceTypingMessage> getTypingMessage() {
    return typingMessage;
  }

  public String getSender() {
    return sender;
  }

  public int getSenderDevice() {
    return senderDevice;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public boolean isNeedsReceipt() {
    return needsReceipt;
  }

  // Loki
  public void setSenderDisplayName(String displayName) { senderDisplayName = Optional.fromNullable(displayName); }

  public void setSenderProfilePictureURL(String url) { senderProfilePictureURL = Optional.fromNullable(url); }
}
