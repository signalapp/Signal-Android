package org.whispersystems.signalservice.api.messages;

import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public final class SignalServiceMetadata {
  private final SignalServiceAddress sender;
  private final int                  senderDevice;
  private final long                 timestamp;
  private final long                 serverReceivedTimestamp;
  private final long                 serverDeliveredTimestamp;
  private final boolean              needsReceipt;
  private final String               serverGuid;
  private final Optional<byte[]>     groupId;

  public SignalServiceMetadata(SignalServiceAddress sender,
                               int senderDevice,
                               long timestamp,
                               long serverReceivedTimestamp,
                               long serverDeliveredTimestamp,
                               boolean needsReceipt,
                               String serverGuid,
                               Optional<byte[]> groupId)
  {
    this.sender                   = sender;
    this.senderDevice             = senderDevice;
    this.timestamp                = timestamp;
    this.serverReceivedTimestamp  = serverReceivedTimestamp;
    this.serverDeliveredTimestamp = serverDeliveredTimestamp;
    this.needsReceipt             = needsReceipt;
    this.serverGuid               = serverGuid;
    this.groupId                  = groupId;
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

  public long getServerReceivedTimestamp() {
    return serverReceivedTimestamp;
  }

  public long getServerDeliveredTimestamp() {
    return serverDeliveredTimestamp;
  }

  public boolean isNeedsReceipt() {
    return needsReceipt;
  }

  public String getServerGuid() {
    return serverGuid;
  }

  public Optional<byte[]> getGroupId() {
    return groupId;
  }
}
