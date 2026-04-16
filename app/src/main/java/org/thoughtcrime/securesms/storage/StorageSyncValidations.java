package org.thoughtcrime.securesms.storage;

import androidx.annotation.NonNull;

import java.util.stream.Collectors;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.signal.core.util.Base64;
import org.signal.core.util.SetUtil;
import org.signal.core.models.ServiceId;
import org.whispersystems.signalservice.api.storage.SignalStorageManifest;
import org.whispersystems.signalservice.api.storage.SignalStorageRecord;
import org.whispersystems.signalservice.api.storage.StorageId;
import org.whispersystems.signalservice.internal.storage.protos.ContactRecord;
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
    validateManifestAndInserts(result.manifest, result.inserts, self);

    if (result.deletes.size() > 0) {
      Set<String> allSetEncoded = result.manifest.storageIds.stream().map(StorageId::getRaw).map(Base64::encodeWithPadding).collect(Collectors.toSet());

      for (byte[] delete : result.deletes) {
        String encoded = Base64.encodeWithPadding(delete);
        if (allSetEncoded.contains(encoded)) {
          throw new DeletePresentInFullIdSetError();
        }
      }
    }

    if (previousManifest.version == 0) {
      Log.i(TAG, "Previous manifest is empty, not bothering with additional validations around the diffs between the two manifests.");
      return;
    }

    if (result.manifest.version != previousManifest.version + 1) {
      throw new IncorrectManifestVersionError();
    }

    if (forcePushPending) {
      Log.i(TAG, "Force push pending, not bothering with additional validations around the diffs between the two manifests.");
      return;
    }

    Set<ByteBuffer> previousIds = previousManifest.storageIds.stream().map(id -> ByteBuffer.wrap(id.getRaw())).collect(Collectors.toSet());
    Set<ByteBuffer> newIds      = result.manifest.storageIds.stream().map(id -> ByteBuffer.wrap(id.getRaw())).collect(Collectors.toSet());

    Set<ByteBuffer> manifestInserts = SetUtil.difference(newIds, previousIds);
    Set<ByteBuffer> manifestDeletes = SetUtil.difference(previousIds, newIds);

    Set<ByteBuffer> declaredInserts = result.inserts.stream().map(r -> ByteBuffer.wrap(r.getId().getRaw())).collect(Collectors.toSet());
    Set<ByteBuffer> declaredDeletes = result.deletes.stream().map(ByteBuffer::wrap).collect(Collectors.toSet());

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
    int accountCount = 0;
    for (StorageId id : manifest.storageIds) {
      accountCount += id.getType() == ManifestRecord.Identifier.Type.ACCOUNT.getValue() ? 1 : 0;
    }

    if (accountCount > 1) {
      throw new MultipleAccountError();
    }

    if (accountCount == 0) {
      throw new MissingAccountError();
    }

    Set<StorageId>  allSet    = new HashSet<>(manifest.storageIds);
    Set<StorageId>  insertSet = new HashSet<>(inserts.stream().map(SignalStorageRecord::getId).collect(Collectors.toList()));
    Set<ByteBuffer> rawIdSet  = allSet.stream().map(id -> ByteBuffer.wrap(id.getRaw())).collect(Collectors.toSet());

    if (allSet.size() != manifest.storageIds.size()) {
      throw new DuplicateStorageIdError();
    }

    if (rawIdSet.size() != allSet.size()) {
      List<StorageId> ids = manifest.getStorageIdsByType().get(ManifestRecord.Identifier.Type.CONTACT.getValue());
      if (ids.size() != new HashSet<>(ids).size()) {
        throw new DuplicateContactIdError();
      }

      ids = manifest.getStorageIdsByType().get(ManifestRecord.Identifier.Type.GROUPV1.getValue());
      if (ids.size() != new HashSet<>(ids).size()) {
        throw new DuplicateGroupV1IdError();
      }

      ids = manifest.getStorageIdsByType().get(ManifestRecord.Identifier.Type.GROUPV2.getValue());
      if (ids.size() != new HashSet<>(ids).size()) {
        throw new DuplicateGroupV2IdError();
      }

      ids = manifest.getStorageIdsByType().get(ManifestRecord.Identifier.Type.STORY_DISTRIBUTION_LIST.getValue());
      if (ids.size() != new HashSet<>(ids).size()) {
        throw new DuplicateDistributionListIdError();
      }

      ids = manifest.getStorageIdsByType().get(ManifestRecord.Identifier.Type.CALL_LINK.getValue());
      if (ids.size() != new HashSet<>(ids).size()) {
        throw new DuplicateCallLinkError();
      }

      ids = manifest.getStorageIdsByType().get(ManifestRecord.Identifier.Type.CHAT_FOLDER.getValue());
      if (ids.size() != new HashSet<>(ids).size()) {
        throw new DuplicateChatFolderError();
      }

      ids = manifest.getStorageIdsByType().get(ManifestRecord.Identifier.Type.NOTIFICATION_PROFILE.getValue());
      if (ids.size() != new HashSet<>(ids).size()) {
        throw new DuplicateNotificationProfileError();
      }

      throw new DuplicateRawIdAcrossTypesError();
    }

    if (inserts.size() > insertSet.size()) {
      throw new DuplicateInsertInWriteError();
    }


    for (SignalStorageRecord insert : inserts) {
      if (!allSet.contains(insert.getId())) {
        throw new InsertNotPresentInFullIdSetError();
      }

      if (insert.isUnknown()) {
        throw new UnknownInsertError();
      }

      if (insert.getProto().contact != null) {
        ContactRecord contact = insert.getProto().contact;

        if (self.requireAci().equals(ServiceId.ACI.parseOrNull(contact.aci, contact.aciBinary)) ||
            (self.getPni().isPresent() && self.requirePni().equals(ServiceId.PNI.parseOrNull(contact.pni, contact.pniBinary))) ||
            (self.getE164().isPresent() && self.requireE164().equals(contact.e164)))
        {
          throw new SelfAddedAsContactError();
        }
      }

      if (insert.getProto().account != null && insert.getProto().account.profileKey.size() == 0) {
        Log.w(TAG, "Uploading a null profile key in our AccountRecord!");
      }
    }
  }

  private static final class DuplicateStorageIdError extends Error {
  }

  private static final class DuplicateRawIdAcrossTypesError extends Error {
  }

  private static final class DuplicateContactIdError extends Error {
  }

  private static final class DuplicateGroupV1IdError extends Error {
  }

  private static final class DuplicateGroupV2IdError extends Error {
  }

  private static final class DuplicateDistributionListIdError extends Error {
  }

  private static final class DuplicateCallLinkError extends Error {
  }

  private static final class DuplicateChatFolderError extends Error {
  }

  private static final class DuplicateInsertInWriteError extends Error {
  }

  private static final class DuplicateNotificationProfileError extends Error {
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
