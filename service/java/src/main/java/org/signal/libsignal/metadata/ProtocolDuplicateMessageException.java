package org.signal.libsignal.metadata;


public class ProtocolDuplicateMessageException extends ProtocolException {
  public ProtocolDuplicateMessageException(Exception e, String sender, int senderDevice) {
    super(e, sender, senderDevice);
  }
}
