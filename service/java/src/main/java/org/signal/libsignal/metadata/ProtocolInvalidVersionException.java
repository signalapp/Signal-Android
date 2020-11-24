package org.signal.libsignal.metadata;


import org.whispersystems.libsignal.InvalidVersionException;

public class ProtocolInvalidVersionException extends ProtocolException {
  public ProtocolInvalidVersionException(InvalidVersionException e, String sender, int senderDevice) {
    super(e, sender, senderDevice);
  }
}
