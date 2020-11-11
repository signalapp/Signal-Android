package org.whispersystems.signalservice.api.messages.calls;

public class OpaqueMessage {

  private final byte[] opaque;

  public OpaqueMessage(byte[] opaque) {
    this.opaque = opaque;
  }

  public byte[] getOpaque() {
    return opaque;
  }
}
