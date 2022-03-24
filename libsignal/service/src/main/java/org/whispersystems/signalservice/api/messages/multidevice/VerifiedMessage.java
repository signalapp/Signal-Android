package org.whispersystems.signalservice.api.messages.multidevice;


import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public class VerifiedMessage {

  public enum VerifiedState {
    DEFAULT, VERIFIED, UNVERIFIED
  }

  private final SignalServiceAddress destination;
  private final IdentityKey          identityKey;
  private final VerifiedState        verified;
  private final long                 timestamp;

  public VerifiedMessage(SignalServiceAddress destination, IdentityKey identityKey, VerifiedState verified, long timestamp) {
    this.destination = destination;
    this.identityKey = identityKey;
    this.verified    = verified;
    this.timestamp   = timestamp;
  }

  public SignalServiceAddress getDestination() {
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
}
