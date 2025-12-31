package org.whispersystems.signalservice.api.messages.calls;


public class AnswerMessage {

  private final long   id;
  private final byte[] opaque;

  public AnswerMessage(long id, byte[] opaque) {
    this.id     = id;
    this.opaque = opaque;
  }

  public long getId() {
    return id;
  }

  public byte[] getOpaque() {
    return opaque;
  }
}
