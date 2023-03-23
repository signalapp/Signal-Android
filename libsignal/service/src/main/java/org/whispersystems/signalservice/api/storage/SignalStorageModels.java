package org.whispersystems.signalservice.api.storage;

import com.google.protobuf.ByteString;

import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.logging.Log;
import org.signal.libsignal.zkgroup.groups.GroupMasterKey;
import org.whispersystems.signalservice.internal.storage.protos.ManifestRecord;
import org.whispersystems.signalservice.internal.storage.protos.StorageItem;
import org.whispersystems.signalservice.internal.storage.protos.StorageManifest;
import org.whispersystems.signalservice.internal.storage.protos.StorageRecord;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class SignalStorageModels {

  private static final String TAG = SignalStorageModels.class.getSimpleName();

  public static SignalStorageManifest remoteToLocalStorageManifest(StorageManifest manifest, StorageKey storageKey) throws IOException, InvalidKeyException {
    byte[]          rawRecord      = SignalStorageCipher.decrypt(storageKey.deriveManifestKey(manifest.getVersion()), manifest.getValue().toByteArray());
    ManifestRecord  manifestRecord = ManifestRecord.parseFrom(rawRecord);
    List<StorageId> ids            = new ArrayList<>(manifestRecord.getIdentifiersCount());

    for (ManifestRecord.Identifier id : manifestRecord.getIdentifiersList()) {
      ids.add(StorageId.forType(id.getRaw().toByteArray(), id.getTypeValue()));
    }

    return new SignalStorageManifest(manifestRecord.getVersion(), manifestRecord.getSourceDevice(), ids);
  }

  public static SignalStorageRecord remoteToLocalStorageRecord(StorageItem item, int type, StorageKey storageKey) throws IOException, InvalidKeyException {
    byte[]        key       = item.getKey().toByteArray();
    byte[]        rawRecord = SignalStorageCipher.decrypt(storageKey.deriveItemKey(key), item.getValue().toByteArray());
    StorageRecord record    = StorageRecord.parseFrom(rawRecord);
    StorageId     id        = StorageId.forType(key, type);

    if (record.hasContact() && type == ManifestRecord.Identifier.Type.CONTACT_VALUE) {
      return SignalStorageRecord.forContact(id, new SignalContactRecord(id, record.getContact()));
    } else if (record.hasGroupV1() && type == ManifestRecord.Identifier.Type.GROUPV1_VALUE) {
      return SignalStorageRecord.forGroupV1(id, new SignalGroupV1Record(id, record.getGroupV1()));
    } else if (record.hasGroupV2() && type == ManifestRecord.Identifier.Type.GROUPV2_VALUE && record.getGroupV2().getMasterKey().size() == GroupMasterKey.SIZE) {
      return SignalStorageRecord.forGroupV2(id, new SignalGroupV2Record(id, record.getGroupV2()));
    } else if (record.hasAccount() && type == ManifestRecord.Identifier.Type.ACCOUNT_VALUE) {
      return SignalStorageRecord.forAccount(id, new SignalAccountRecord(id, record.getAccount()));
    } else if (record.hasStoryDistributionList() && type == ManifestRecord.Identifier.Type.STORY_DISTRIBUTION_LIST_VALUE) {
      return SignalStorageRecord.forStoryDistributionList(id, new SignalStoryDistributionListRecord(id, record.getStoryDistributionList()));
    } else {
      if (StorageId.isKnownType(type)) {
        Log.w(TAG, "StorageId is of known type (" + type + "), but the data is bad! Falling back to unknown.");
      }
      return SignalStorageRecord.forUnknown(StorageId.forType(key, type));
    }
  }

  public static StorageItem localToRemoteStorageRecord(SignalStorageRecord record, StorageKey storageKey) {
    StorageRecord.Builder builder = StorageRecord.newBuilder();

    if (record.getContact().isPresent()) {
      builder.setContact(record.getContact().get().toProto());
    } else if (record.getGroupV1().isPresent()) {
      builder.setGroupV1(record.getGroupV1().get().toProto());
    } else if (record.getGroupV2().isPresent()) {
      builder.setGroupV2(record.getGroupV2().get().toProto());
    } else if (record.getAccount().isPresent()) {
      builder.setAccount(record.getAccount().get().toProto());
    } else if (record.getStoryDistributionList().isPresent()) {
      builder.setStoryDistributionList(record.getStoryDistributionList().get().toProto());
    } else {
      throw new InvalidStorageWriteError();
    }

    StorageRecord  remoteRecord    = builder.build();
    StorageItemKey itemKey         = storageKey.deriveItemKey(record.getId().getRaw());
    byte[]         encryptedRecord = SignalStorageCipher.encrypt(itemKey, remoteRecord.toByteArray());

    return StorageItem.newBuilder()
                      .setKey(ByteString.copyFrom(record.getId().getRaw()))
                      .setValue(ByteString.copyFrom(encryptedRecord))
                      .build();
  }

  private static class InvalidStorageWriteError extends Error {
  }
}
