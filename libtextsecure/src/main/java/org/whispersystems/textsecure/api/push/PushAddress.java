package org.whispersystems.textsecure.api.push;

public class PushAddress {

  public static final int DEFAULT_DEVICE_ID = 1;

  private final long   recipientId;
  private final String e164number;
  private final int    deviceId;
  private final String relay;

  public PushAddress(long recipientId, String e164number, int deviceId, String relay) {
    this.recipientId = recipientId;
    this.e164number  = e164number;
    this.deviceId    = deviceId;
    this.relay       = relay;
  }

  public String getNumber() {
    return e164number;
  }

  public String getRelay() {
    return relay;
  }

  public long getRecipientId() {
    return recipientId;
  }

  public int getDeviceId() {
    return deviceId;
  }
}
