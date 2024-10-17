package org.whispersystems.signalservice.api.storage;

import org.signal.core.util.ProtoUtil;
import org.whispersystems.signalservice.internal.storage.protos.ManifestRecord;
import org.whispersystems.signalservice.internal.storage.protos.StorageManifest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import okio.ByteString;

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
      StorageManifest manifest       = StorageManifest.ADAPTER.decode(serialized);
      ManifestRecord  manifestRecord = ManifestRecord.ADAPTER.decode(manifest.value_);
      List<StorageId> ids            = new ArrayList<>(manifestRecord.identifiers.size());

      for (ManifestRecord.Identifier id : manifestRecord.identifiers) {
        ids.add(StorageId.forType(id.raw.toByteArray(), id.type.getValue()));
      }

      return new SignalStorageManifest(manifest.version, manifestRecord.sourceDevice, ids);
    } catch (IOException e) {
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
    List<StorageId> list = storageIdsByType.get(ManifestRecord.Identifier.Type.ACCOUNT.getValue());

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
      ManifestRecord.Identifier.Type type = ManifestRecord.Identifier.Type.Companion.fromValue(id.getType());
      if (type != null) {
        ids.add(new ManifestRecord.Identifier.Builder()
                                             .type(type)
                                             .raw(ByteString.of(id.getRaw()))
                                             .build());
      } else {
        ByteString unknownEnum = ProtoUtil.writeUnknownEnumValue(StorageRecordProtoUtil.STORAGE_ID_TYPE_TAG, id.getType());
        ids.add(new ManifestRecord.Identifier(ByteString.of(id.getRaw()), ManifestRecord.Identifier.Type.UNKNOWN, unknownEnum));
      }
    }

    ManifestRecord manifestRecord = new ManifestRecord.Builder()
                                                      .identifiers(ids)
                                                      .sourceDevice(sourceDeviceId)
                                                      .build();

    return new StorageManifest.Builder()
                              .version(version)
                              .value_(manifestRecord.encodeByteString())
                              .build()
                              .encode();
  }
}
