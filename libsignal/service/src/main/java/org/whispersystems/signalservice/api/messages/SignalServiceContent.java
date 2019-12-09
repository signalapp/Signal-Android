/*
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.messages;

import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.calls.SignalServiceCallMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public class SignalServiceContent {

  private final SignalServiceAddress sender;
  private final int                  senderDevice;
  private final long                 timestamp;
  private final boolean              needsReceipt;

  private final Optional<SignalServiceDataMessage>    message;
  private final Optional<SignalServiceSyncMessage>    synchronizeMessage;
  private final Optional<SignalServiceCallMessage>    callMessage;
  private final Optional<SignalServiceReceiptMessage> readMessage;
  private final Optional<SignalServiceTypingMessage>  typingMessage;

  public SignalServiceContent(SignalServiceDataMessage message, SignalServiceAddress sender, int senderDevice, long timestamp, boolean needsReceipt) {
    this.sender       = sender;
    this.senderDevice = senderDevice;
    this.timestamp    = timestamp;
    this.needsReceipt = needsReceipt;

    this.message            = Optional.fromNullable(message);
    this.synchronizeMessage = Optional.absent();
    this.callMessage        = Optional.absent();
    this.readMessage        = Optional.absent();
    this.typingMessage      = Optional.absent();
  }

  public SignalServiceContent(SignalServiceSyncMessage synchronizeMessage, SignalServiceAddress sender, int senderDevice, long timestamp, boolean needsReceipt) {
    this.sender       = sender;
    this.senderDevice = senderDevice;
    this.timestamp    = timestamp;
    this.needsReceipt = needsReceipt;

    this.message            = Optional.absent();
    this.synchronizeMessage = Optional.fromNullable(synchronizeMessage);
    this.callMessage        = Optional.absent();
    this.readMessage        = Optional.absent();
    this.typingMessage      = Optional.absent();
  }

  public SignalServiceContent(SignalServiceCallMessage callMessage, SignalServiceAddress sender, int senderDevice, long timestamp, boolean needsReceipt) {
    this.sender       = sender;
    this.senderDevice = senderDevice;
    this.timestamp    = timestamp;
    this.needsReceipt = needsReceipt;

    this.message            = Optional.absent();
    this.synchronizeMessage = Optional.absent();
    this.callMessage        = Optional.of(callMessage);
    this.readMessage        = Optional.absent();
    this.typingMessage      = Optional.absent();
  }

  public SignalServiceContent(SignalServiceReceiptMessage receiptMessage, SignalServiceAddress sender, int senderDevice, long timestamp, boolean needsReceipt) {
    this.sender       = sender;
    this.senderDevice = senderDevice;
    this.timestamp    = timestamp;
    this.needsReceipt = needsReceipt;

    this.message            = Optional.absent();
    this.synchronizeMessage = Optional.absent();
    this.callMessage        = Optional.absent();
    this.readMessage        = Optional.of(receiptMessage);
    this.typingMessage      = Optional.absent();
  }

  public SignalServiceContent(SignalServiceTypingMessage typingMessage, SignalServiceAddress sender, int senderDevice, long timestamp, boolean needsReceipt) {
    this.sender       = sender;
    this.senderDevice = senderDevice;
    this.timestamp    = timestamp;
    this.needsReceipt = needsReceipt;

    this.message            = Optional.absent();
    this.synchronizeMessage = Optional.absent();
    this.callMessage        = Optional.absent();
    this.readMessage        = Optional.absent();
    this.typingMessage      = Optional.of(typingMessage);
  }

  public Optional<SignalServiceDataMessage> getDataMessage() {
    return message;
  }

  public Optional<SignalServiceSyncMessage> getSyncMessage() {
    return synchronizeMessage;
  }

  public Optional<SignalServiceCallMessage> getCallMessage() {
    return callMessage;
  }

  public Optional<SignalServiceReceiptMessage> getReceiptMessage() {
    return readMessage;
  }

  public Optional<SignalServiceTypingMessage> getTypingMessage() {
    return typingMessage;
  }

  public SignalServiceAddress getSender() {
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
}
