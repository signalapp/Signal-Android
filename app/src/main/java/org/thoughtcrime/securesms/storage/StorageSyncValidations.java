package org.thoughtcrime.securesms.storage;

import androidx.annotation.NonNull;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.Base64;
import org.signal.core.util.SetUtil;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.storage.SignalContactRecord;
import org.whispersystems.signalservice.api.storage.SignalStorageManifest;
import org.whispersystems.signalservice.api.storage.SignalStorageRecord;
import org.whispersystems.signalservice.api.storage.StorageId;
import org.whispersystems.signalservice.internal.storage.protos.ManifestRecord;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class StorageSyncValidations {

  private static final String TAG = Log.tag(StorageSyncValidations.class);

  private StorageSyncValidations() {}

  public static void validate(@NonNull StorageSyncHelper.WriteOperationResult result,
                              @NonNull SignalStorageManifest previousManifest,
                              boolean forcePushPending,
                              @NonNull Recipient self)
  {
    validateManifestAndInserts(result.getManifest(), result.getInserts(), self);

    if (result.getDeletes().size() > 0) {
      Set<String> allSetEncoded = Stream.of(result.getManifest().getStorageIds()).map(StorageId::getRaw).map(Base64::encodeBytes).collect(Collectors.toSet());

      for (byte[] delete : result.getDeletes()) {
        String encoded = Base64.encodeBytes(delete);
        if (allSetEncoded.contains(encoded)) {
          throw new DeletePresentInFullIdSetError();
        }
      }
    }

    if (previousManifest.getVersion() == 0) {
      Log.i(TAG, "Previous manifest is empty, not bothering with additional validations around the diffs between the two manifests.");
      return;
    }

    if (result.getManifest().getVersion() != previousManifest.getVersion() + 1) {
      throw new IncorrectManifestVersionError();
    }

    if (forcePushPending) {
      Log.i(TAG, "Force push pending, not bothering with additional validations around the diffs between the two manifests.");
      return;
    }

    Set<ByteBuffer> previousIds = Stream.of(previousManifest.getStorageIds()).map(id -> ByteBuffer.wrap(id.getRaw())).collect(Collectors.toSet());
    Set<ByteBuffer> newIds      = Stream.of(result.getManifest().getStorageIds()).map(id -> ByteBuffer.wrap(id.getRaw())).collect(Collectors.toSet());

    Set<ByteBuffer> manifestInserts = SetUtil.difference(newIds, previousIds);
    Set<ByteBuffer> manifestDeletes = SetUtil.difference(previousIds, newIds);

    Set<ByteBuffer> declaredInserts = Stream.of(result.getInserts()).map(r -> ByteBuffer.wrap(r.getId().getRaw())).collect(Collectors.toSet());
    Set<ByteBuffer> declaredDeletes = Stream.of(result.getDeletes()).map(ByteBuffer::wrap).collect(Collectors.toSet());

    if (declaredInserts.size() > manifestInserts.size()) {
      Log.w(TAG, "DeclaredInserts: " + declaredInserts.size() + ", ManifestInserts: " + manifestInserts.size());
      throw new MoreInsertsThanExpectedError();
    }

    if (declaredInserts.size() < manifestInserts.size()) {
      Log.w(TAG, "DeclaredInserts: " + declaredInserts.size() + ", ManifestInserts: " + manifestInserts.size());
      throw new LessInsertsThanExpectedError();
    }

    if (!declaredInserts.containsAll(manifestInserts)) {
      throw new InsertMismatchError();
    }

    if (declaredDeletes.size() > manifestDeletes.size()) {
      Log.w(TAG, "DeclaredDeletes: " + declaredDeletes.size() + ", ManifestDeletes: " + manifestDeletes.size());
      throw new MoreDeletesThanExpectedError();
    }

    if (declaredDeletes.size() < manifestDeletes.size()) {
      Log.w(TAG, "DeclaredDeletes: " + declaredDeletes.size() + ", ManifestDeletes: " + manifestDeletes.size());
      throw new LessDeletesThanExpectedError();
    }

    if (!declaredDeletes.containsAll(manifestDeletes)) {
      throw new DeleteMismatchError();
    }
  }


  public static void validateForcePush(@NonNull SignalStorageManifest manifest, @NonNull List<SignalStorageRecord> inserts, @NonNull Recipient self) {
    validateManifestAndInserts(manifest, inserts, self);
  }

  private static void validateManifestAndInserts(@NonNull SignalStorageManifest manifest, @NonNull List<SignalStorageRecord> inserts, @NonNull Recipient self) {
    Set<StorageId>  allSet    = new HashSet<>(manifest.getStorageIds());
    Set<StorageId>  insertSet = new HashSet<>(Stream.of(inserts).map(SignalStorageRecord::getId).toList());
    Set<ByteBuffer> rawIdSet  = Stream.of(allSet).map(id -> ByteBuffer.wrap(id.getRaw())).collect(Collectors.toSet());

    if (allSet.size() != manifest.getStorageIds().size()) {
      throw new DuplicateStorageIdError();
    }

    if (rawIdSet.size() != allSet.size()) {
      throw new DuplicateRawIdError();
    }

    if (inserts.size() > insertSet.size()) {
      throw new DuplicateInsertInWriteError();
    }

    int accountCount = 0;
    for (StorageId id : manifest.getStorageIds()) {
      accountCount += id.getType() == ManifestRecord.Identifier.Type.ACCOUNT_VALUE ? 1 : 0;
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

      if (insert.getContact().isPresent()) {
        SignalContactRecord contact = insert.getContact().get();

        if (self.requireServiceId().equals(contact.getServiceId()) ||
            self.requirePni().equals(contact.getPni().orElse(null)) ||
            self.requireE164().equals(contact.getNumber().orElse("")))
        {
          throw new SelfAddedAsContactError();
        }
      }

      if (insert.getAccount().isPresent() && !insert.getAccount().get().getProfileKey().isPresent()) {
        Log.w(TAG, "Uploading a null profile key in our AccountRecord!");
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

  private static final class MultipleAccountError extends Error {
  }

  private static final class MissingAccountError extends Error {
  }

  private static final class SelfAddedAsContactError extends Error {
  }

  private static final class IncorrectManifestVersionError extends Error {
  }

  private static final class MoreInsertsThanExpectedError extends Error {
  }

  private static final class LessInsertsThanExpectedError extends Error {
  }

  private static final class InsertMismatchError extends Error {
  }

  private static final class MoreDeletesThanExpectedError extends Error {
  }

  private static final class LessDeletesThanExpectedError extends Error {
  }

  private static final class DeleteMismatchError extends Error {
  }
}
