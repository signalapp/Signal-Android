package org.whispersystems.textsecure.push;

import java.util.List;

public class PreKeyList {

  private List<String> keys;

  public PreKeyList(List<String> keys) {
    this.keys = keys;
  }

  public List<String> getKeys() {
    return keys;
  }
}
