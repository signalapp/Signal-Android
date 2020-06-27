package org.whispersystems.signalservice.api.storage;

import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.internal.storage.protos.ManifestRecord;
import org.whispersystems.signalservice.internal.storage.protos.StorageManifest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SignalStorageManifest {
  private final long                          version;
  private final List<StorageId>               storageIds;
  private final Map<Integer, List<StorageId>> storageIdsByType;

  public SignalStorageManifest(long version, List<StorageId> storageIds) {
    this.version          = version;
    this.storageIds       = storageIds;
    this.storageIdsByType = new HashMap<>();

    for (StorageId id : storageIds) {
      List<StorageId> list = storageIdsByType.get(id.getType());
      if (list == null) {
        list = new ArrayList<>();
      }
      list.add(id);
      storageIdsByType.put(id.getType(), list);
    }
  }

  public long getVersion() {
    return version;
  }

  public List<StorageId> getStorageIds() {
    return storageIds;
  }

  public Optional<StorageId> getAccountStorageId() {
    List<StorageId> list = storageIdsByType.get(ManifestRecord.Identifier.Type.ACCOUNT_VALUE);

    if (list != null && list.size() > 0) {
      return Optional.of(list.get(0));
    } else {
      return Optional.absent();
    }
  }
}
