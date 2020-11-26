package org.session.libsignal.metadata;


import org.session.libsignal.libsignal.LegacyMessageException;

public class ProtocolLegacyMessageException extends ProtocolException {
  public ProtocolLegacyMessageException(LegacyMessageException e, String sender, int senderDeviceId) {
    super(e, sender, senderDeviceId);
  }
}
