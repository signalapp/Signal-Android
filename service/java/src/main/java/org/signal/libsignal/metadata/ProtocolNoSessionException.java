package org.signal.libsignal.metadata;


import org.whispersystems.libsignal.NoSessionException;

public class ProtocolNoSessionException extends ProtocolException {
  public ProtocolNoSessionException(NoSessionException e, String sender, int senderDevice) {
    super(e, sender, senderDevice);
  }
}
