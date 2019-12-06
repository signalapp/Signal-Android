package org.whispersystems.signalservice.api.storage;

import java.util.List;

public class SignalStorageManifest {
  private final long         version;
  private final List<byte[]> storageKeys;

  public SignalStorageManifest(long version, List<byte[]> storageKeys) {
    this.version     = version;
    this.storageKeys = storageKeys;
  }

  public long getVersion() {
    return version;
  }

  public List<byte[]> getStorageKeys() {
    return storageKeys;
  }
}
