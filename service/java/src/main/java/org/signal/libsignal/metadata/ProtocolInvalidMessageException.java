package org.signal.libsignal.metadata;


import org.whispersystems.libsignal.InvalidMessageException;

public class ProtocolInvalidMessageException extends ProtocolException {
  public ProtocolInvalidMessageException(InvalidMessageException e, String sender, int senderDevice) {
    super(e, sender, senderDevice);
  }
}
