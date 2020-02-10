package org.whispersystems.signalservice.api.storage;

import com.google.protobuf.ByteString;

import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.internal.storage.protos.ContactRecord;
import org.whispersystems.signalservice.internal.storage.protos.GroupV1Record;
import org.whispersystems.signalservice.internal.storage.protos.StorageItem;
import org.whispersystems.signalservice.internal.storage.protos.StorageRecord;

import java.io.IOException;

public final class SignalStorageModels {

  public static SignalStorageRecord remoteToLocalStorageRecord(StorageItem item, StorageKey storageKey) throws IOException, InvalidKeyException {
    byte[]        key       = item.getKey().toByteArray();
    byte[]        rawRecord = SignalStorageCipher.decrypt(storageKey.deriveItemKey(key), item.getValue().toByteArray());
    StorageRecord record    = StorageRecord.parseFrom(rawRecord);

    switch (record.getType()) {
      case StorageRecord.Type.CONTACT_VALUE:
        return SignalStorageRecord.forContact(key, remoteToLocalContactRecord(key, record.getContact()));
      case StorageRecord.Type.GROUPV1_VALUE:
        return SignalStorageRecord.forGroupV1(key, remoteToLocalGroupV1Record(key, record.getGroupV1()));
      default:
        return SignalStorageRecord.forUnknown(key, record.getType());
    }
  }

  public static StorageItem localToRemoteStorageRecord(SignalStorageRecord record, StorageKey storageKey) {
    StorageRecord.Builder builder = StorageRecord.newBuilder();

    if (record.getContact().isPresent()) {
      builder.setContact(localToRemoteContactRecord(record.getContact().get()));
    } else if (record.getGroupV1().isPresent()) {
      builder.setGroupV1(localToRemoteGroupV1Record(record.getGroupV1().get()));
    } else {
      throw new InvalidStorageWriteError();
    }

    builder.setType(record.getType());

    StorageRecord  remoteRecord    = builder.build();
    StorageItemKey itemKey         = storageKey.deriveItemKey(record.getKey());
    byte[]         encryptedRecord = SignalStorageCipher.encrypt(itemKey, remoteRecord.toByteArray());

    return StorageItem.newBuilder()
                      .setKey(ByteString.copyFrom(record.getKey()))
                      .setValue(ByteString.copyFrom(encryptedRecord))
                      .build();
  }

  private static SignalContactRecord remoteToLocalContactRecord(byte[] key, ContactRecord contact) {
    SignalServiceAddress        address = new SignalServiceAddress(UuidUtil.parseOrNull(contact.getServiceUuid()), contact.getServiceE164());
    SignalContactRecord.Builder builder = new SignalContactRecord.Builder(key, address);

    if (contact.hasBlocked()) {
      builder.setBlocked(contact.getBlocked());
    }

    if (contact.hasWhitelisted()) {
      builder.setProfileSharingEnabled(contact.getWhitelisted());
    }

    if (contact.hasNickname()) {
      builder.setNickname(contact.getNickname());
    }

    if (contact.hasProfile()) {
      if (contact.getProfile().hasKey()) {
        builder.setProfileKey(contact.getProfile().getKey().toByteArray());
      }

      if (contact.getProfile().hasGivenName()) {
        builder.setGivenName(contact.getProfile().getGivenName());
      }

      if (contact.getProfile().hasFamilyName()) {
        builder.setFamilyName(contact.getProfile().getFamilyName());
      }

      if (contact.getProfile().hasUsername()) {
        builder.setUsername(contact.getProfile().getUsername());
      }
    }

    if (contact.hasIdentity()) {
      if (contact.getIdentity().hasKey()) {
        builder.setIdentityKey(contact.getIdentity().getKey().toByteArray());
      }

      if (contact.getIdentity().hasState()) {
        switch (contact.getIdentity().getState()) {
          case VERIFIED:   builder.setIdentityState(SignalContactRecord.IdentityState.VERIFIED);
          case UNVERIFIED: builder.setIdentityState(SignalContactRecord.IdentityState.UNVERIFIED);
          default:         builder.setIdentityState(SignalContactRecord.IdentityState.DEFAULT);
        }
      }
    }

    return builder.build();
  }

  private static SignalGroupV1Record remoteToLocalGroupV1Record(byte[] key, GroupV1Record groupV1) {
    SignalGroupV1Record.Builder builder = new SignalGroupV1Record.Builder(key, groupV1.getId().toByteArray());

    if (groupV1.hasBlocked()) {
      builder.setBlocked(groupV1.getBlocked());
    }

    if (groupV1.hasWhitelisted()) {
      builder.setProfileSharingEnabled(groupV1.getWhitelisted());
    }

    return builder.build();
  }

  private static ContactRecord localToRemoteContactRecord(SignalContactRecord contact) {
    ContactRecord.Builder contactRecordBuilder = ContactRecord.newBuilder()
                                                              .setBlocked(contact.isBlocked())
                                                              .setWhitelisted(contact.isProfileSharingEnabled());
    if (contact.getAddress().getNumber().isPresent()) {
      contactRecordBuilder.setServiceE164(contact.getAddress().getNumber().get());
    }

    if (contact.getAddress().getUuid().isPresent()) {
      contactRecordBuilder.setServiceUuid(contact.getAddress().getUuid().get().toString());
    }

    if (contact.getNickname().isPresent()) {
      contactRecordBuilder.setNickname(contact.getNickname().get());
    }

    ContactRecord.Identity.Builder identityBuilder = ContactRecord.Identity.newBuilder();

    switch (contact.getIdentityState()) {
      case VERIFIED:   identityBuilder.setState(ContactRecord.Identity.State.VERIFIED);
      case UNVERIFIED: identityBuilder.setState(ContactRecord.Identity.State.UNVERIFIED);
      case DEFAULT:    identityBuilder.setState(ContactRecord.Identity.State.DEFAULT);
    }

    if (contact.getIdentityKey().isPresent()) {
      identityBuilder.setKey(ByteString.copyFrom(contact.getIdentityKey().get()));
    }

    contactRecordBuilder.setIdentity(identityBuilder.build());

    ContactRecord.Profile.Builder profileBuilder = ContactRecord.Profile.newBuilder();

    if (contact.getProfileKey().isPresent()) {
      profileBuilder.setKey(ByteString.copyFrom(contact.getProfileKey().get()));
    }

    if (contact.getGivenName().isPresent()) {
      profileBuilder.setGivenName(contact.getGivenName().get());
    }

    if (contact.getFamilyName().isPresent()) {
      profileBuilder.setFamilyName(contact.getFamilyName().get());
    }

    if (contact.getUsername().isPresent()) {
      profileBuilder.setUsername(contact.getUsername().get());
    }

    contactRecordBuilder.setProfile(profileBuilder.build());

    return contactRecordBuilder.build();
  }

  private static GroupV1Record localToRemoteGroupV1Record(SignalGroupV1Record groupV1) {
    return GroupV1Record.newBuilder()
                        .setId(ByteString.copyFrom(groupV1.getGroupId()))
                        .setBlocked(groupV1.isBlocked())
                        .setWhitelisted(groupV1.isProfileSharingEnabled())
                        .build();
  }

  private static class InvalidStorageWriteError extends Error {
  }
}
