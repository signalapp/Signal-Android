package org.whispersystems.textsecure.internal.push;

import com.google.thoughtcrimegson.GsonBuilder;

import org.whispersystems.textsecure.api.push.SignedPreKeyEntity;

public class PreKeyResponseItem {

  private int                deviceId;
  private int                registrationId;
  private SignedPreKeyEntity signedPreKey;
  private PreKeyEntity       preKey;

  public int getDeviceId() {
    return deviceId;
  }

  public int getRegistrationId() {
    return registrationId;
  }

  public SignedPreKeyEntity getSignedPreKey() {
    return signedPreKey;
  }

  public PreKeyEntity getPreKey() {
    return preKey;
  }

  public static GsonBuilder forBuilder(GsonBuilder builder) {
    return SignedPreKeyEntity.forBuilder(builder);
  }
}
