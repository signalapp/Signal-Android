package org.signal.libsignal.metadata;


public class ProtocolInvalidKeyIdException extends ProtocolException {
  public ProtocolInvalidKeyIdException(Exception e, String sender, int senderDevice) {
    super(e, sender, senderDevice);
  }
}
