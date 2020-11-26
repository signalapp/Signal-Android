package org.session.libsignal.metadata;


import org.session.libsignal.libsignal.NoSessionException;

public class ProtocolNoSessionException extends ProtocolException {
  public ProtocolNoSessionException(NoSessionException e, String sender, int senderDevice) {
    super(e, sender, senderDevice);
  }
}
