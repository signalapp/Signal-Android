package org.whispersystems.textsecure.storage;

public class RecipientDevice {

  public static final int DEFAULT_DEVICE_ID = 1;

  private final long recipientId;
  private final int  deviceId;

  public RecipientDevice(long recipientId, int deviceId) {
    this.recipientId = recipientId;
    this.deviceId    = deviceId;
  }

  public long getRecipientId() {
    return recipientId;
  }

  public int getDeviceId() {
    return deviceId;
  }

  public CanonicalRecipient getRecipient() {
    return new CanonicalRecipient() {
      @Override
      public long getRecipientId() {
        return recipientId;
      }
    };
  }
}
