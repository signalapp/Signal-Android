package org.whispersystems.signalservice.api.messages.calls;


public class IceUpdateMessage {

  private final long   id;
  private final String sdpMid;
  private final int    sdpMLineIndex;
  private final String sdp;

  public IceUpdateMessage(long id, String sdpMid, int sdpMLineIndex, String sdp) {
    this.id            = id;
    this.sdpMid        = sdpMid;
    this.sdpMLineIndex = sdpMLineIndex;
    this.sdp           = sdp;
  }

  public String getSdpMid() {
    return sdpMid;
  }

  public int getSdpMLineIndex() {
    return sdpMLineIndex;
  }

  public String getSdp() {
    return sdp;
  }

  public long getId() {
    return id;
  }
}
