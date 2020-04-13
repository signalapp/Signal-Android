package org.whispersystems.signalservice.api.messages;

import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public final class SignalServiceMetadata {
  private final SignalServiceAddress sender;
  private final int                  senderDevice;
  private final long                 timestamp;
  private final long                 serverTimestamp;
  private final boolean              needsReceipt;

  public SignalServiceMetadata(SignalServiceAddress sender, int senderDevice, long timestamp, long serverTimestamp, boolean needsReceipt) {
    this.sender          = sender;
    this.senderDevice    = senderDevice;
    this.timestamp       = timestamp;
    this.serverTimestamp = serverTimestamp;
    this.needsReceipt    = needsReceipt;
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

  public long getServerTimestamp() {
    return serverTimestamp;
  }

  public boolean isNeedsReceipt() {
    return needsReceipt;
  }
}
