package org.whispersystems.signalservice.api.storage;

import org.signal.core.util.ProtoUtil;
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

import okio.ByteString;

public final class SignalStorageModels {

  private static final String TAG = SignalStorageModels.class.getSimpleName();

  public static SignalStorageManifest remoteToLocalStorageManifest(StorageManifest manifest, StorageKey storageKey) throws IOException, InvalidKeyException {
    byte[]          rawRecord      = SignalStorageCipher.decrypt(storageKey.deriveManifestKey(manifest.version), manifest.value_.toByteArray());
    ManifestRecord  manifestRecord = ManifestRecord.ADAPTER.decode(rawRecord);
    List<StorageId> ids            = new ArrayList<>(manifestRecord.identifiers.size());

    for (ManifestRecord.Identifier id : manifestRecord.identifiers) {
      int typeValue = (id.type != ManifestRecord.Identifier.Type.UNKNOWN) ? id.type.getValue()
                                                                          : ProtoUtil.getUnknownEnumValue(id, StorageRecordProtoUtil.STORAGE_ID_TYPE_TAG);

      ids.add(StorageId.forType(id.raw.toByteArray(), typeValue));
    }

    return new SignalStorageManifest(manifestRecord.version, manifestRecord.sourceDevice, ids);
  }

  public static SignalStorageRecord remoteToLocalStorageRecord(StorageItem item, int type, StorageKey storageKey) throws IOException, InvalidKeyException {
    byte[]        key       = item.key.toByteArray();
    byte[]        rawRecord = SignalStorageCipher.decrypt(storageKey.deriveItemKey(key), item.value_.toByteArray());
    StorageRecord record    = StorageRecord.ADAPTER.decode(rawRecord);
    StorageId     id        = StorageId.forType(key, type);

    if (record.contact != null && type == ManifestRecord.Identifier.Type.CONTACT.getValue()) {
      return SignalStorageRecord.forContact(id, new SignalContactRecord(id, record.contact));
    } else if (record.groupV1 != null && type == ManifestRecord.Identifier.Type.GROUPV1.getValue()) {
      return SignalStorageRecord.forGroupV1(id, new SignalGroupV1Record(id, record.groupV1));
    } else if (record.groupV2 != null && type == ManifestRecord.Identifier.Type.GROUPV2.getValue() && record.groupV2.masterKey.size() == GroupMasterKey.SIZE) {
      return SignalStorageRecord.forGroupV2(id, new SignalGroupV2Record(id, record.groupV2));
    } else if (record.account != null && type == ManifestRecord.Identifier.Type.ACCOUNT.getValue()) {
      return SignalStorageRecord.forAccount(id, new SignalAccountRecord(id, record.account));
    } else if (record.storyDistributionList != null && type == ManifestRecord.Identifier.Type.STORY_DISTRIBUTION_LIST.getValue()) {
      return SignalStorageRecord.forStoryDistributionList(id, new SignalStoryDistributionListRecord(id, record.storyDistributionList));
    } else if (record.callLink != null && type == ManifestRecord.Identifier.Type.CALL_LINK.getValue()) {
      return SignalStorageRecord.forCallLink(id, new SignalCallLinkRecord(id, record.callLink));
    }else {
      if (StorageId.isKnownType(type)) {
        Log.w(TAG, "StorageId is of known type (" + type + "), but the data is bad! Falling back to unknown.");
      }
      return SignalStorageRecord.forUnknown(StorageId.forType(key, type));
    }
  }

  public static StorageItem localToRemoteStorageRecord(SignalStorageRecord record, StorageKey storageKey) {
    StorageRecord.Builder builder = new StorageRecord.Builder();

    if (record.getContact().isPresent()) {
      builder.contact(record.getContact().get().toProto());
    } else if (record.getGroupV1().isPresent()) {
      builder.groupV1(record.getGroupV1().get().toProto());
    } else if (record.getGroupV2().isPresent()) {
      builder.groupV2(record.getGroupV2().get().toProto());
    } else if (record.getAccount().isPresent()) {
      builder.account(record.getAccount().get().toProto());
    } else if (record.getStoryDistributionList().isPresent()) {
      builder.storyDistributionList(record.getStoryDistributionList().get().toProto());
    } else if (record.getCallLink().isPresent()) {
      builder.callLink(record.getCallLink().get().toProto());
    } else {
      throw new InvalidStorageWriteError();
    }

    StorageRecord  remoteRecord    = builder.build();
    StorageItemKey itemKey         = storageKey.deriveItemKey(record.getId().getRaw());
    byte[]         encryptedRecord = SignalStorageCipher.encrypt(itemKey, remoteRecord.encode());

    return new StorageItem.Builder()
                          .key(ByteString.of(record.getId().getRaw()))
                          .value_(ByteString.of(encryptedRecord))
                          .build();
  }

  private static class InvalidStorageWriteError extends Error {
  }
}
