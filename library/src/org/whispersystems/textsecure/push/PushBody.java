package org.whispersystems.textsecure.push;

public class PushBody {

  private final int    type;
  private final int    remoteRegistrationId;
  private final byte[] body;

  public PushBody(int type, int remoteRegistrationId, byte[] body) {
    this.type                 = type;
    this.remoteRegistrationId = remoteRegistrationId;
    this.body                 = body;
  }

  public int getType() {
    return type;
  }

  public byte[] getBody() {
    return body;
  }

  public int getRemoteRegistrationId() {
    return remoteRegistrationId;
  }
}
