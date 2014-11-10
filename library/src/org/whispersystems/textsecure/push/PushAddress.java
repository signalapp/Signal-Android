package org.whispersystems.textsecure.push;

import org.whispersystems.textsecure.storage.RecipientDevice;

public class PushAddress extends RecipientDevice {

  private final String e164number;
  private final String relay;

  public PushAddress(long recipientId, String e164number, int deviceId, String relay) {
    super(recipientId, deviceId);
    this.e164number  = e164number;
    this.relay       = relay;
  }

  public String getNumber() {
    return e164number;
  }

  public String getRelay() {
    return relay;
  }

}
