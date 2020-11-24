package org.signal.libsignal.metadata;


public abstract class ProtocolException extends Exception {

  private final String sender;
  private final int senderDevice;

  public ProtocolException(Exception e, String sender, int senderDevice) {
    super(e);
    this.sender       = sender;
    this.senderDevice = senderDevice;
  }

  public String getSender() {
    return sender;
  }

  public int getSenderDevice() {
    return senderDevice;
  }
}
