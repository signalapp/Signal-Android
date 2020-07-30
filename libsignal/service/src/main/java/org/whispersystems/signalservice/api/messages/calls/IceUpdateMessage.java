package org.whispersystems.signalservice.api.messages.calls;


public class IceUpdateMessage {

  private final long   id;
  private final byte[] opaque;
  private final String sdp;

  public IceUpdateMessage(long id, byte[] opaque, String sdp) {
    this.id     = id;
    this.opaque = opaque;
    this.sdp    = sdp;
  }

  public long getId() {
    return id;
  }

  public byte[] getOpaque() {
    return opaque;
  }

  public String getSdp() {
    return sdp;
  }
}
