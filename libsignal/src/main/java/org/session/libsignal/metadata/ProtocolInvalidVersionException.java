package org.session.libsignal.metadata;


import org.session.libsignal.libsignal.InvalidVersionException;

public class ProtocolInvalidVersionException extends ProtocolException {
  public ProtocolInvalidVersionException(InvalidVersionException e, String sender, int senderDevice) {
    super(e, sender, senderDevice);
  }
}
