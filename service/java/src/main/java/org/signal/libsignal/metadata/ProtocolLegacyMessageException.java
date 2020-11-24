package org.signal.libsignal.metadata;


import org.whispersystems.libsignal.LegacyMessageException;

public class ProtocolLegacyMessageException extends ProtocolException {
  public ProtocolLegacyMessageException(LegacyMessageException e, String sender, int senderDeviceId) {
    super(e, sender, senderDeviceId);
  }
}
