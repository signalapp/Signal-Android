package org.whispersystems.signalservice.api.messages.multidevice;


import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.signalservice.loki.protocol.meta.TTLUtilities;

public class VerifiedMessage {

  public enum VerifiedState {
    DEFAULT, VERIFIED, UNVERIFIED
  }

  private final String        destination;
  private final IdentityKey   identityKey;
  private final VerifiedState verified;
  private final long          timestamp;

  public VerifiedMessage(String destination, IdentityKey identityKey, VerifiedState verified, long timestamp) {
    this.destination = destination;
    this.identityKey = identityKey;
    this.verified    = verified;
    this.timestamp   = timestamp;
  }

  public String getDestination() {
    return destination;
  }

  public IdentityKey getIdentityKey() {
    return identityKey;
  }

  public VerifiedState getVerified() {
    return verified;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public int getTTL() { return TTLUtilities.getTTL(TTLUtilities.MessageType.Verified); }
}
