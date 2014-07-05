package org.whispersystems.textsecure.push;

import com.google.thoughtcrimegson.GsonBuilder;

import org.whispersystems.libaxolotl.IdentityKey;

import java.util.List;

public class PreKeyState {

  private IdentityKey        identityKey;
  private List<PreKeyEntity> preKeys;
  private PreKeyEntity       lastResortKey;
  private DeviceKeyEntity    deviceKey;


  public PreKeyState(List<PreKeyEntity> preKeys, PreKeyEntity lastResortKey,
                     DeviceKeyEntity deviceKey, IdentityKey identityKey)
  {
    this.preKeys       = preKeys;
    this.lastResortKey = lastResortKey;
    this.deviceKey     = deviceKey;
    this.identityKey   = identityKey;
  }

  public static String toJson(PreKeyState state) {
    GsonBuilder builder = new GsonBuilder();
    return DeviceKeyEntity.forBuilder(builder)
                          .registerTypeAdapter(IdentityKey.class, new PreKeyResponse.IdentityKeyJsonAdapter())
                          .create().toJson(state);
  }
}
