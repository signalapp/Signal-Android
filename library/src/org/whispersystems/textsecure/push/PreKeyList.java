package org.whispersystems.textsecure.push;

import java.util.List;

public class PreKeyList {

  private List<PreKeyEntity> keys;

  public PreKeyList(List<PreKeyEntity> keys) {
    this.keys = keys;
  }

  public List<PreKeyEntity> getKeys() {
    return keys;
  }

  public static String toJson(PreKeyList entity) {
    return PreKeyEntity.getBuilder().create().toJson(entity);
  }
}
