package org.whispersystems.signalservice.api.storage;

import java.util.List;

public class SignalStorageManifest {
  private final long            version;
  private final List<StorageId> storageIds;

  public SignalStorageManifest(long version, List<StorageId> storageIds) {
    this.version     = version;
    this.storageIds = storageIds;
  }

  public long getVersion() {
    return version;
  }

  public List<StorageId> getStorageIds() {
    return storageIds;
  }
}
