package org.whispersystems.signalservice.api.storage;

import com.google.protobuf.ByteString;

import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.internal.storage.protos.ContactRecord;
import org.whispersystems.signalservice.internal.storage.protos.StorageItem;
import org.whispersystems.signalservice.internal.storage.protos.StorageRecord;

import java.io.IOException;

public final class SignalStorageModels {

  public static SignalStorageRecord remoteToLocalStorageRecord(StorageItem item, SignalStorageCipher cipher) throws IOException, InvalidKeyException {
    byte[]        rawRecord  = cipher.decrypt(item.getValue().toByteArray());
    StorageRecord record     = StorageRecord.parseFrom(rawRecord);
    byte[]        storageKey = item.getKey().toByteArray();

    if (record.getType() == StorageRecord.Type.CONTACT_VALUE && record.hasContact()) {
      return SignalStorageRecord.forContact(storageKey, remoteToLocalContactRecord(storageKey, record.getContact()));
    } else {
      return SignalStorageRecord.forUnknown(storageKey, record.getType());
    }
  }

  public static StorageItem localToRemoteStorageRecord(SignalStorageRecord record, SignalStorageCipher cipher) throws IOException {
    StorageRecord.Builder builder = StorageRecord.newBuilder();

    if (record.getContact().isPresent()) {
      builder.setContact(localToRemoteContactRecord(record.getContact().get()));
    } else {
      throw new InvalidStorageWriteError();
    }

    builder.setType(record.getType());

    StorageRecord remoteRecord    = builder.build();
    byte[]        encryptedRecord = cipher.encrypt(remoteRecord.toByteArray());

    return StorageItem.newBuilder()
                      .setKey(ByteString.copyFrom(record.getKey()))
                      .setValue(ByteString.copyFrom(encryptedRecord))
                      .build();
  }

  public static SignalContactRecord remoteToLocalContactRecord(byte[] storageKey, ContactRecord contact) throws IOException {
    SignalServiceAddress        address   = new SignalServiceAddress(UuidUtil.parseOrNull(contact.getServiceUuid()), contact.getServiceE164());
    SignalContactRecord.Builder builder   = new SignalContactRecord.Builder(storageKey, address);

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

      if (contact.getProfile().hasName()) {
        builder.setProfileName(contact.getProfile().getName());
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
          default:         builder.setIdentityState(SignalContactRecord.IdentityState.VERIFIED);
        }
      }
    }

    return builder.build();
  }

  public static ContactRecord localToRemoteContactRecord(SignalContactRecord contact) {
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

    if (contact.getProfileName().isPresent()) {
      profileBuilder.setName(contact.getProfileName().get());
    }

    if (contact.getUsername().isPresent()) {
      profileBuilder.setUsername(contact.getUsername().get());
    }

    contactRecordBuilder.setProfile(profileBuilder.build());

    return contactRecordBuilder.build();
  }

  private static class InvalidStorageWriteError extends Error {
  }
}
