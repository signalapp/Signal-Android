package org.session.libsignal.metadata;


import org.session.libsignal.libsignal.InvalidKeyException;

public class ProtocolInvalidKeyException extends ProtocolException {
  public ProtocolInvalidKeyException(InvalidKeyException e, String sender, int senderDevice) {
    super(e, sender, senderDevice);
  }
}
