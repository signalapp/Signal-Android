package org.whispersystems.jobqueue;

public class EncryptionKeys {

  private transient final String keys;

  public EncryptionKeys(String keys) {
    this.keys = keys;
  }

  public String getKeys() {
    return keys;
  }
}
