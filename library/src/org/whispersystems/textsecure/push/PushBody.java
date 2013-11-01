package org.whispersystems.textsecure.push;

public class PushBody {

  private final int    type;
  private final byte[] body;

  public PushBody(int type, byte[] body) {
    this.type = type;
    this.body = body;
  }

  public int getType() {
    return type;
  }

  public byte[] getBody() {
    return body;
  }
}
