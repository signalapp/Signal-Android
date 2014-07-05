package org.whispersystems.textsecure.push;

import com.google.thoughtcrimegson.GsonBuilder;

public class PreKeyResponseItem {

  private int             deviceId;
  private int             registrationId;
  private DeviceKeyEntity deviceKey;
  private PreKeyEntity    preKey;

  public int getDeviceId() {
    return deviceId;
  }

  public int getRegistrationId() {
    return registrationId;
  }

  public DeviceKeyEntity getDeviceKey() {
    return deviceKey;
  }

  public PreKeyEntity getPreKey() {
    return preKey;
  }

  public static GsonBuilder forBuilder(GsonBuilder builder) {
    return DeviceKeyEntity.forBuilder(builder);
  }
}
