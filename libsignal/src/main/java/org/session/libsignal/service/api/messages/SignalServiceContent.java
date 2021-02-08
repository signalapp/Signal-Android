/*
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.session.libsignal.service.api.messages;

import org.session.libsignal.libsignal.util.guava.Optional;
import org.session.libsignal.service.api.messages.SignalServiceDataMessage;
import org.session.libsignal.service.api.messages.SignalServiceNullMessage;
import org.session.libsignal.service.api.messages.SignalServiceReceiptMessage;
import org.session.libsignal.service.api.messages.SignalServiceTypingMessage;
import org.session.libsignal.service.api.messages.calls.SignalServiceCallMessage;
import org.session.libsignal.service.api.messages.multidevice.ConfigurationMessage;
import org.session.libsignal.service.api.messages.multidevice.SignalServiceSyncMessage;
import org.session.libsignal.service.internal.push.SignalServiceProtos;
import org.session.libsignal.service.loki.protocol.shelved.multidevice.DeviceLink;
import org.session.libsignal.service.loki.protocol.sessionmanagement.PreKeyBundleMessage;

public class SignalServiceContent {
  private final String  sender;
  private final int     senderDevice;
  private final long    timestamp;
  private final boolean needsReceipt;

  // Loki
  private final boolean isDeviceUnlinkingRequest;

  private Optional<SignalServiceDataMessage>          message;
  private Optional<SignalServiceSyncMessage>          synchronizeMessage;
  private final Optional<SignalServiceCallMessage>    callMessage;
  private final Optional<SignalServiceNullMessage>    nullMessage;
  private final Optional<SignalServiceReceiptMessage> readMessage;
  private final Optional<SignalServiceTypingMessage>  typingMessage;

  // Loki
  private final Optional<DeviceLink>           deviceLink;
  public Optional<SignalServiceProtos.Content> configurationMessageProto = Optional.absent();
  public Optional<PreKeyBundleMessage>         preKeyBundleMessage       = Optional.absent();
  public Optional<String>                      senderDisplayName         = Optional.absent();
  public Optional<String>                      senderProfilePictureURL   = Optional.absent();

  public SignalServiceContent(SignalServiceDataMessage message, String sender, int senderDevice, long timestamp, boolean needsReceipt, boolean isDeviceUnlinkingRequest) {
    this.sender                   = sender;
    this.senderDevice             = senderDevice;
    this.timestamp                = timestamp;
    this.needsReceipt             = needsReceipt;
    this.message                  = Optional.fromNullable(message);
    this.synchronizeMessage       = Optional.absent();
    this.callMessage              = Optional.absent();
    this.nullMessage              = Optional.absent();
    this.readMessage              = Optional.absent();
    this.typingMessage            = Optional.absent();
    this.deviceLink               = Optional.absent();
    this.isDeviceUnlinkingRequest = isDeviceUnlinkingRequest;
  }

  public SignalServiceContent(SignalServiceSyncMessage synchronizeMessage, String sender, int senderDevice, long timestamp) {
    this.sender                   = sender;
    this.senderDevice             = senderDevice;
    this.timestamp                = timestamp;
    this.needsReceipt             = false;
    this.message                  = Optional.absent();
    this.synchronizeMessage       = Optional.fromNullable(synchronizeMessage);
    this.callMessage              = Optional.absent();
    this.nullMessage              = Optional.absent();
    this.readMessage              = Optional.absent();
    this.typingMessage            = Optional.absent();
    this.deviceLink               = Optional.absent();
    this.isDeviceUnlinkingRequest = false;
  }

  public SignalServiceContent(SignalServiceCallMessage callMessage, String sender, int senderDevice, long timestamp) {
    this.sender                   = sender;
    this.senderDevice             = senderDevice;
    this.timestamp                = timestamp;
    this.needsReceipt             = false;
    this.message                  = Optional.absent();
    this.synchronizeMessage       = Optional.absent();
    this.callMessage              = Optional.of(callMessage);
    this.nullMessage              = Optional.absent();
    this.readMessage              = Optional.absent();
    this.typingMessage            = Optional.absent();
    this.deviceLink               = Optional.absent();
    this.isDeviceUnlinkingRequest = false;
  }

  public SignalServiceContent(SignalServiceReceiptMessage receiptMessage, String sender, int senderDevice, long timestamp) {
    this.sender                   = sender;
    this.senderDevice             = senderDevice;
    this.timestamp                = timestamp;
    this.needsReceipt             = false;
    this.message                  = Optional.absent();
    this.synchronizeMessage       = Optional.absent();
    this.callMessage              = Optional.absent();
    this.nullMessage              = Optional.absent();
    this.readMessage              = Optional.of(receiptMessage);
    this.typingMessage            = Optional.absent();
    this.deviceLink               = Optional.absent();
    this.isDeviceUnlinkingRequest = false;
  }

  public SignalServiceContent(SignalServiceTypingMessage typingMessage, String sender, int senderDevice, long timestamp) {
    this.sender                   = sender;
    this.senderDevice             = senderDevice;
    this.timestamp                = timestamp;
    this.needsReceipt             = false;
    this.message                  = Optional.absent();
    this.synchronizeMessage       = Optional.absent();
    this.callMessage              = Optional.absent();
    this.nullMessage              = Optional.absent();
    this.readMessage              = Optional.absent();
    this.typingMessage            = Optional.of(typingMessage);
    this.deviceLink               = Optional.absent();
    this.isDeviceUnlinkingRequest = false;
  }

  public SignalServiceContent(DeviceLink deviceLink, String sender, int senderDevice, long timestamp) {
    this.sender                   = sender;
    this.senderDevice             = senderDevice;
    this.timestamp                = timestamp;
    this.needsReceipt             = false;
    this.message                  = Optional.absent();
    this.synchronizeMessage       = Optional.absent();
    this.callMessage              = Optional.absent();
    this.nullMessage              = Optional.absent();
    this.readMessage              = Optional.absent();
    this.typingMessage            = Optional.absent();
    this.deviceLink               = Optional.fromNullable(deviceLink);
    this.isDeviceUnlinkingRequest = false;
  }

  public SignalServiceContent(SignalServiceProtos.Content configurationMessageProto, String sender, int senderDevice, long timestamp) {
    this.sender                    = sender;
    this.senderDevice              = senderDevice;
    this.timestamp                 = timestamp;
    this.needsReceipt              = false;
    this.message                   = Optional.absent();
    this.synchronizeMessage        = Optional.absent();
    this.callMessage               = Optional.absent();
    this.nullMessage               = Optional.absent();
    this.readMessage               = Optional.absent();
    this.typingMessage             = Optional.absent();
    this.deviceLink                = Optional.absent();
    this.configurationMessageProto = Optional.fromNullable(configurationMessageProto);
    this.isDeviceUnlinkingRequest  = false;
  }

  public SignalServiceContent(SignalServiceNullMessage nullMessage, String sender, int senderDevice, long timestamp) {
    this.sender                   = sender;
    this.senderDevice             = senderDevice;
    this.timestamp                = timestamp;
    this.needsReceipt             = false;
    this.message                  = Optional.absent();
    this.synchronizeMessage       = Optional.absent();
    this.callMessage              = Optional.absent();
    this.nullMessage              = Optional.of(nullMessage);
    this.readMessage              = Optional.absent();
    this.typingMessage            = Optional.absent();
    this.deviceLink               = Optional.absent();
    this.isDeviceUnlinkingRequest = false;
  }

  public Optional<SignalServiceDataMessage> getDataMessage() {
    return message;
  }

  public void setDataMessage(SignalServiceDataMessage message) { this.message = Optional.fromNullable(message); }

  public Optional<SignalServiceSyncMessage> getSyncMessage() { return synchronizeMessage; }

  public void setSyncMessage(SignalServiceSyncMessage message) { this.synchronizeMessage = Optional.fromNullable(message); }

  public Optional<SignalServiceCallMessage> getCallMessage() {
    return callMessage;
  }

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

  public Optional<SignalServiceNullMessage> getNullMessage() { return nullMessage; }

  // Loki
  public boolean isDeviceUnlinkingRequest() { return isDeviceUnlinkingRequest; }

  public Optional<DeviceLink> getDeviceLink() { return deviceLink; }

  public void setPreKeyBundleMessage(PreKeyBundleMessage preKeyBundleMessage) { this.preKeyBundleMessage = Optional.fromNullable(preKeyBundleMessage); }

  public void setSenderDisplayName(String displayName) { senderDisplayName = Optional.fromNullable(displayName); }

  public void setSenderProfilePictureURL(String url) { senderProfilePictureURL = Optional.fromNullable(url); }
}
