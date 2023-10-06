package org.whispersystems.signalservice.api.messages.calls;


public class AnswerMessage {

  private final long   id;
  private final String sdp;
  private final byte[] opaque;

  public AnswerMessage(long id, String sdp, byte[] opaque) {
    this.id     = id;
    this.sdp    = sdp;
    this.opaque = opaque;
  }

  public String getSdp() {
    return sdp;
  }

  public long getId() {
    return id;
  }

  public byte[] getOpaque() {
    return opaque;
  }
}
