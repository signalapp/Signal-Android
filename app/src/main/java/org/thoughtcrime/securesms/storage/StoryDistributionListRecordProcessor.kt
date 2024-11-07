package org.thoughtcrime.securesms.storage

import org.signal.core.util.StringUtil
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.whispersystems.signalservice.api.push.DistributionId
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.api.storage.SignalStoryDistributionListRecord
import org.whispersystems.signalservice.api.util.UuidUtil
import java.io.IOException
import java.util.Optional

class StoryDistributionListRecordProcessor : DefaultStorageRecordProcessor<SignalStoryDistributionListRecord>() {

  companion object {
    private val TAG = Log.tag(StoryDistributionListRecordProcessor::class.java)
  }

  private var haveSeenMyStory = false

  /**
   * At a minimum, we require:
   *
   *  - A valid identifier
   *  - A non-visually-empty name field OR a deleted at timestamp
   */
  override fun isInvalid(remote: SignalStoryDistributionListRecord): Boolean {
    val remoteUuid = UuidUtil.parseOrNull(remote.identifier)
    if (remoteUuid == null) {
      Log.d(TAG, "Bad distribution list identifier -- marking as invalid")
      return true
    }

    val isMyStory = remoteUuid == DistributionId.MY_STORY.asUuid()
    if (haveSeenMyStory && isMyStory) {
      Log.w(TAG, "Found an additional MyStory record -- marking as invalid")
      return true
    }

    haveSeenMyStory = haveSeenMyStory or isMyStory

    if (remote.deletedAtTimestamp > 0L) {
      if (isMyStory) {
        Log.w(TAG, "Refusing to delete My Story -- marking as invalid")
        return true
      } else {
        return false
      }
    }

    if (StringUtil.isVisuallyEmpty(remote.name)) {
      Log.d(TAG, "Bad distribution list name (visually empty) -- marking as invalid")
      return true
    }

    return false
  }

  override fun getMatching(remote: SignalStoryDistributionListRecord, keyGenerator: StorageKeyGenerator): Optional<SignalStoryDistributionListRecord> {
    Log.d(TAG, "Attempting to get matching record...")
    val matching = SignalDatabase.distributionLists.getRecipientIdForSyncRecord(remote)
    if (matching == null && UuidUtil.parseOrThrow(remote.identifier) == DistributionId.MY_STORY.asUuid()) {
      Log.e(TAG, "Cannot find matching database record for My Story.")
      throw MyStoryDoesNotExistException()
    }

    if (matching != null) {
      Log.d(TAG, "Found a matching RecipientId for the distribution list...")
      val recordForSync = SignalDatabase.recipients.getRecordForSync(matching)
      if (recordForSync == null) {
        Log.e(TAG, "Could not find a record for the recipient id in the recipient table")
        throw IllegalStateException("Found matching recipient but couldn't generate record for sync.")
      }

      if (recordForSync.recipientType.id != RecipientTable.RecipientType.DISTRIBUTION_LIST.id) {
        Log.d(TAG, "Record has an incorrect group type.")
        throw InvalidGroupTypeException()
      }

      val record = StorageSyncModels.localToRemoteRecord(recordForSync).storyDistributionList
      if (record.isPresent) {
        Log.d(TAG, "Found a matching record.")
        return record
      } else {
        Log.e(TAG, "Could not resolve the record")
        throw UnexpectedEmptyOptionalException()
      }
    } else {
      Log.d(TAG, "Could not find a matching record. Returning an empty.")
      return Optional.empty()
    }
  }

  override fun merge(remote: SignalStoryDistributionListRecord, local: SignalStoryDistributionListRecord, keyGenerator: StorageKeyGenerator): SignalStoryDistributionListRecord {
    val unknownFields = remote.serializeUnknownFields()
    val identifier = remote.identifier
    val name = remote.name
    val recipients = remote.recipients
    val deletedAtTimestamp = remote.deletedAtTimestamp
    val allowsReplies = remote.allowsReplies()
    val isBlockList = remote.isBlockList

    val matchesRemote = doParamsMatch(
      record = remote,
      unknownFields = unknownFields,
      identifier = identifier,
      name = name,
      recipients = recipients,
      deletedAtTimestamp = deletedAtTimestamp,
      allowsReplies = allowsReplies,
      isBlockList = isBlockList
    )
    val matchesLocal = doParamsMatch(
      record = local,
      unknownFields = unknownFields,
      identifier = identifier,
      name = name,
      recipients = recipients,
      deletedAtTimestamp = deletedAtTimestamp,
      allowsReplies = allowsReplies,
      isBlockList = isBlockList
    )

    return if (matchesRemote) {
      remote
    } else if (matchesLocal) {
      local
    } else {
      SignalStoryDistributionListRecord.Builder(keyGenerator.generate(), unknownFields)
        .setIdentifier(identifier)
        .setName(name)
        .setRecipients(recipients)
        .setDeletedAtTimestamp(deletedAtTimestamp)
        .setAllowsReplies(allowsReplies)
        .setIsBlockList(isBlockList)
        .build()
    }
  }

  @Throws(IOException::class)
  override fun insertLocal(record: SignalStoryDistributionListRecord) {
    SignalDatabase.distributionLists.applyStorageSyncStoryDistributionListInsert(record)
  }

  override fun updateLocal(update: StorageRecordUpdate<SignalStoryDistributionListRecord>) {
    SignalDatabase.distributionLists.applyStorageSyncStoryDistributionListUpdate(update)
  }

  override fun compare(o1: SignalStoryDistributionListRecord, o2: SignalStoryDistributionListRecord): Int {
    return if (o1.identifier.contentEquals(o2.identifier)) {
      0
    } else {
      1
    }
  }

  private fun doParamsMatch(
    record: SignalStoryDistributionListRecord,
    unknownFields: ByteArray?,
    identifier: ByteArray?,
    name: String?,
    recipients: List<SignalServiceAddress>,
    deletedAtTimestamp: Long,
    allowsReplies: Boolean,
    isBlockList: Boolean
  ): Boolean {
    return unknownFields.contentEquals(record.serializeUnknownFields()) &&
      identifier.contentEquals(record.identifier) &&
      name == record.name &&
      recipients == record.recipients &&
      deletedAtTimestamp == record.deletedAtTimestamp &&
      allowsReplies == record.allowsReplies() &&
      isBlockList == record.isBlockList
  }

  /**
   * Thrown when the RecipientSettings object for a given distribution list is not the
   * correct group type (4).
   */
  private class InvalidGroupTypeException : RuntimeException()

  /**
   * Thrown when the distribution list object returned from the storage sync helper is
   * absent, even though a RecipientSettings was found.
   */
  private class UnexpectedEmptyOptionalException : RuntimeException()

  /**
   * Thrown when we try to ge the matching record for the "My Story" distribution ID but
   * it isn't in the database.
   */
  private class MyStoryDoesNotExistException : RuntimeException()
}
