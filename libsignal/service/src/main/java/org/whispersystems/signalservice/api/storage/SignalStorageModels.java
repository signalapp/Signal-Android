package org.whispersystems.signalservice.api.storage;

import com.google.protobuf.ByteString;

import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.internal.storage.protos.ContactRecord;
import org.whispersystems.signalservice.internal.storage.protos.GroupV1Record;
import org.whispersystems.signalservice.internal.storage.protos.ManifestRecord;
import org.whispersystems.signalservice.internal.storage.protos.StorageItem;
import org.whispersystems.signalservice.internal.storage.protos.StorageManifest;
import org.whispersystems.signalservice.internal.storage.protos.StorageRecord;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class SignalStorageModels {

  public static SignalStorageManifest remoteToLocalStorageManifest(StorageManifest manifest, StorageKey storageKey) throws IOException, InvalidKeyException {
    byte[]          rawRecord      = SignalStorageCipher.decrypt(storageKey.deriveManifestKey(manifest.getVersion()), manifest.getValue().toByteArray());
    ManifestRecord  manifestRecord = ManifestRecord.parseFrom(rawRecord);
    List<StorageId> ids            = new ArrayList<>(manifestRecord.getIdentifiersCount());

    for (ManifestRecord.Identifier id : manifestRecord.getIdentifiersList()) {
      ids.add(StorageId.forType(id.getRaw().toByteArray(), id.getType().getNumber()));
    }

    return new SignalStorageManifest(manifestRecord.getVersion(), ids);
  }

  public static SignalStorageRecord remoteToLocalStorageRecord(StorageItem item, int type, StorageKey storageKey) throws IOException, InvalidKeyException {
    byte[]        key       = item.getKey().toByteArray();
    byte[]        rawRecord = SignalStorageCipher.decrypt(storageKey.deriveItemKey(key), item.getValue().toByteArray());
    StorageRecord record    = StorageRecord.parseFrom(rawRecord);

    if (record.hasContact() && type == ManifestRecord.Identifier.Type.CONTACT_VALUE) {
      return SignalStorageRecord.forContact(StorageId.forContact(key), remoteToLocalContactRecord(key, record.getContact()));
    } else if (record.hasGroupV1() && type == ManifestRecord.Identifier.Type.GROUPV1_VALUE) {
      return SignalStorageRecord.forGroupV1(StorageId.forGroupV1(key), remoteToLocalGroupV1Record(key, record.getGroupV1()));
    } else {
      return SignalStorageRecord.forUnknown(StorageId.forType(key, type));
    }
  }

  public static StorageItem localToRemoteStorageRecord(SignalStorageRecord record, StorageKey storageKey) {
    StorageRecord.Builder builder = StorageRecord.newBuilder();

    if (record.getContact().isPresent()) {
      builder.setContact(record.getContact().get().toProto());
    } else if (record.getGroupV1().isPresent()) {
      builder.setGroupV1(record.getGroupV1().get().toProto());
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

  private static SignalContactRecord remoteToLocalContactRecord(byte[] key, ContactRecord contact) {
    SignalServiceAddress address = new SignalServiceAddress(UuidUtil.parseOrNull(contact.getServiceUuid()), contact.getServiceE164());

    return new SignalContactRecord.Builder(key, address)
                                  .setBlocked(contact.getBlocked())
                                  .setProfileSharingEnabled(contact.getWhitelisted())
                                  .setProfileKey(contact.getProfileKey().toByteArray())
                                  .setGivenName(contact.getGivenName())
                                  .setFamilyName(contact.getFamilyName())
                                  .setUsername(contact.getUsername())
                                  .setIdentityKey(contact.getIdentityKey().toByteArray())
                                  .setIdentityState(contact.getIdentityState())
                                  .build();
  }

  private static SignalGroupV1Record remoteToLocalGroupV1Record(byte[] key, GroupV1Record groupV1) {
    return new SignalGroupV1Record.Builder(key, groupV1.getId().toByteArray())
                                  .setBlocked(groupV1.getBlocked())
                                  .setProfileSharingEnabled(groupV1.getWhitelisted())
                                  .build();
  }

  private static class InvalidStorageWriteError extends Error {
  }
}
