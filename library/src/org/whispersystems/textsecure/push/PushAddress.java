package org.whispersystems.textsecure.push;

import android.content.Context;

import org.whispersystems.textsecure.directory.Directory;
import org.whispersystems.textsecure.storage.RecipientDevice;

public class PushAddress extends RecipientDevice {

  private final String e164number;
  private final String relay;

  private PushAddress(long recipientId, String e164number, int deviceId, String relay) {
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

  public static PushAddress create(Context context, long recipientId, String e164number, int deviceId) {
    String relay = Directory.getInstance(context).getRelay(e164number);
    return new PushAddress(recipientId, e164number, deviceId, relay);
  }

}
