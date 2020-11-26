package org.session.libsignal.metadata;


import org.session.libsignal.libsignal.InvalidMessageException;

public class ProtocolInvalidMessageException extends ProtocolException {
  public ProtocolInvalidMessageException(InvalidMessageException e, String sender, int senderDevice) {
    super(e, sender, senderDevice);
  }
}
