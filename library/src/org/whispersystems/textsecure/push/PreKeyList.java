package org.whispersystems.textsecure.push;

import java.util.List;

public class PreKeyList {

  private PreKeyEntity lastResortKey;
  private List<PreKeyEntity> keys;

  public PreKeyList(PreKeyEntity lastResortKey, List<PreKeyEntity> keys) {
    this.keys          = keys;
    this.lastResortKey = lastResortKey;
  }

  public List<PreKeyEntity> getKeys() {
    return keys;
  }

  public static String toJson(PreKeyList entity) {
    return PreKeyEntity.getBuilder().create().toJson(entity);
  }

  public static PreKeyList fromJson(String serialized) {
    return PreKeyEntity.getBuilder().create().fromJson(serialized, PreKeyList.class);
  }

  public PreKeyEntity getLastResortKey() {
    return lastResortKey;
  }
}
