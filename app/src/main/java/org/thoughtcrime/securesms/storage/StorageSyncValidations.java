package org.thoughtcrime.securesms.storage;

import androidx.annotation.NonNull;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.Base64;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.storage.SignalStorageManifest;
import org.whispersystems.signalservice.api.storage.SignalStorageRecord;
import org.whispersystems.signalservice.api.storage.StorageId;
import org.whispersystems.signalservice.internal.storage.protos.ManifestRecord;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class StorageSyncValidations {

  private StorageSyncValidations() {}

  public static void validate(@NonNull StorageSyncHelper.WriteOperationResult result) {
    validateManifestAndInserts(result.getManifest(), result.getInserts());

    if (result.getDeletes().size() > 0) {
      Set<String> allSetEncoded = Stream.of(result.getManifest().getStorageIds()).map(StorageId::getRaw).map(Base64::encodeBytes).collect(Collectors.toSet());

      for (byte[] delete : result.getDeletes()) {
        String encoded = Base64.encodeBytes(delete);
        if (allSetEncoded.contains(encoded)) {
          throw new DeletePresentInFullIdSetError();
        }
      }
    }
  }


  public static void validateForcePush(@NonNull SignalStorageManifest manifest, @NonNull List<SignalStorageRecord> inserts) {
    validateManifestAndInserts(manifest, inserts);
  }

  private static void validateManifestAndInserts(@NonNull SignalStorageManifest manifest, @NonNull List<SignalStorageRecord> inserts) {
    Set<StorageId>  allSet    = new HashSet<>(manifest.getStorageIds());
    Set<StorageId>  insertSet = new HashSet<>(Stream.of(inserts).map(SignalStorageRecord::getId).toList());
    Set<ByteBuffer> rawIdSet  = Stream.of(allSet).map(id -> ByteBuffer.wrap(id.getRaw())).collect(Collectors.toSet());

    if (allSet.size() != manifest.getStorageIds().size()) {
      throw new DuplicateStorageIdError();
    }

    if (rawIdSet.size() != allSet.size()) {
      throw new DuplicateRawIdError();
    }

    int accountCount = 0;
    for (StorageId id : manifest.getStorageIds()) {
      accountCount += id.getType() == ManifestRecord.Identifier.Type.ACCOUNT_VALUE ? 1 : 0;
    }

    if (inserts.size() > insertSet.size()) {
      throw new DuplicateInsertInWriteError();
    }

    if (accountCount > 1) {
      throw new MultipleAccountError();
    }

    if (accountCount == 0) {
      throw new MissingAccountError();
    }

    for (SignalStorageRecord insert : inserts) {
      if (!allSet.contains(insert.getId())) {
        throw new InsertNotPresentInFullIdSetError();
      }

      if (insert.isUnknown()) {
        throw new UnknownInsertError();
      }

      if (insert.getContact().isPresent()) {
        Recipient            self    = Recipient.self().fresh();
        SignalServiceAddress address = insert.getContact().get().getAddress();
        if (self.getE164().get().equals(address.getNumber().or("")) || self.getUuid().get().equals(address.getUuid().orNull())) {
          throw new SelfAddedAsContactError();
        }
      }
    }
  }

  private static final class DuplicateStorageIdError extends Error {
  }

  private static final class DuplicateRawIdError extends Error {
  }

  private static final class DuplicateInsertInWriteError extends Error {
  }

  private static final class InsertNotPresentInFullIdSetError extends Error {
  }

  private static final class DeletePresentInFullIdSetError extends Error {
  }

  private static final class UnknownInsertError extends Error {
  }

  private static final class MultipleAccountError extends Error {
  }

  private static final class MissingAccountError extends Error {
  }

  private static final class SelfAddedAsContactError extends Error {
  }
}
