package org.session.libsignal.exceptions;


public class ProtocolInvalidMessageException extends ProtocolException {
  public ProtocolInvalidMessageException(InvalidMessageException e, String sender, int senderDevice) {
    super(e, sender, senderDevice);
  }
}
