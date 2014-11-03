package org.whispersystems.textsecure.push;

import com.google.thoughtcrimegson.GsonBuilder;

import org.whispersystems.libaxolotl.IdentityKey;

import java.util.List;

public class PreKeyState {

  private IdentityKey        identityKey;
  private List<PreKeyEntity> preKeys;
  private PreKeyEntity       lastResortKey;
  private SignedPreKeyEntity signedPreKey;


  public PreKeyState(List<PreKeyEntity> preKeys, PreKeyEntity lastResortKey,
                     SignedPreKeyEntity signedPreKey, IdentityKey identityKey)
  {
    this.preKeys       = preKeys;
    this.lastResortKey = lastResortKey;
    this.signedPreKey  = signedPreKey;
    this.identityKey   = identityKey;
  }

  public static String toJson(PreKeyState state) {
    GsonBuilder builder = new GsonBuilder();
    return SignedPreKeyEntity.forBuilder(builder)
                          .registerTypeAdapter(IdentityKey.class, new PreKeyResponse.IdentityKeyJsonAdapter())
                          .create().toJson(state);
  }
}
