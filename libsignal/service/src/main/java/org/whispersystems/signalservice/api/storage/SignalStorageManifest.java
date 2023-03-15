package org.whispersystems.signalservice.api.storage;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.whispersystems.signalservice.internal.storage.protos.ManifestRecord;
import org.whispersystems.signalservice.internal.storage.protos.StorageManifest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SignalStorageManifest {
  public static final SignalStorageManifest EMPTY = new SignalStorageManifest(0, 1, Collections.emptyList());

  private final long                          version;
  private final int                           sourceDeviceId;
  private final List<StorageId>               storageIds;
  private final Map<Integer, List<StorageId>> storageIdsByType;

  public SignalStorageManifest(long version, int sourceDeviceId, List<StorageId> storageIds) {
    this.version          = version;
    this.sourceDeviceId   = sourceDeviceId;
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

  public static SignalStorageManifest deserialize(byte[] serialized) {
    try {
      StorageManifest manifest       = StorageManifest.parseFrom(serialized);
      ManifestRecord  manifestRecord = ManifestRecord.parseFrom(manifest.getValue());
      List<StorageId> ids            = new ArrayList<>(manifestRecord.getIdentifiersCount());

      for (ManifestRecord.Identifier id : manifestRecord.getIdentifiersList()) {
        ids.add(StorageId.forType(id.getRaw().toByteArray(), id.getTypeValue()));
      }

      return new SignalStorageManifest(manifest.getVersion(), manifestRecord.getSourceDevice(), ids);
    } catch (InvalidProtocolBufferException e) {
      throw new AssertionError(e);
    }
  }

  public long getVersion() {
    return version;
  }

  public int getSourceDeviceId() {
    return sourceDeviceId;
  }

  public String getVersionString() {
    return version + "." + sourceDeviceId;
  }

  public List<StorageId> getStorageIds() {
    return storageIds;
  }

  public Optional<StorageId> getAccountStorageId() {
    List<StorageId> list = storageIdsByType.get(ManifestRecord.Identifier.Type.ACCOUNT_VALUE);

    if (list != null && list.size() > 0) {
      return Optional.of(list.get(0));
    } else {
      return Optional.empty();
    }
  }

  public Map<Integer, List<StorageId>> getStorageIdsByType() {
    return storageIdsByType;
  }

  public byte[] serialize() {
    List<ManifestRecord.Identifier> ids = new ArrayList<>(storageIds.size());

    for (StorageId id : storageIds) {
      ids.add(ManifestRecord.Identifier.newBuilder()
                                       .setTypeValue(id.getType())
                                       .setRaw(ByteString.copyFrom(id.getRaw()))
                                       .build());
    }

    ManifestRecord manifestRecord = ManifestRecord.newBuilder()
                                                  .addAllIdentifiers(ids)
                                                  .setSourceDevice(sourceDeviceId)
                                                  .build();

    return StorageManifest.newBuilder()
                          .setVersion(version)
                          .setValue(manifestRecord.toByteString())
                          .build()
                          .toByteArray();
  }
}
