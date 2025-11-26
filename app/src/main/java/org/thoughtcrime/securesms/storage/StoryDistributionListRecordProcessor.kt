package org.thoughtcrime.securesms.storage

import org.signal.core.util.StringUtil
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.whispersystems.signalservice.api.push.DistributionId
import org.whispersystems.signalservice.api.storage.SignalStoryDistributionListRecord
import org.whispersystems.signalservice.api.storage.StorageId
import org.whispersystems.signalservice.api.storage.toSignalStoryDistributionListRecord
import org.whispersystems.signalservice.api.util.OptionalUtil.asOptional
import org.whispersystems.signalservice.api.util.UuidUtil
import java.io.IOException
import java.util.Optional

/**
 * Record processor for [SignalStoryDistributionListRecord].
 * Handles merging and updating our local store when processing remote dlist storage records.
 */
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
    val remoteUuid = UuidUtil.parseOrNull(remote.proto.identifier)
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

    if (remote.proto.deletedAtTimestamp > 0L) {
      if (isMyStory) {
        Log.w(TAG, "Refusing to delete My Story -- marking as invalid")
        return true
      } else {
        return false
      }
    }

    if (StringUtil.isVisuallyEmpty(remote.proto.name)) {
      Log.d(TAG, "Bad distribution list name (visually empty) -- marking as invalid")
      return true
    }

    return false
  }

  override fun getMatching(remote: SignalStoryDistributionListRecord, keyGenerator: StorageKeyGenerator): Optional<SignalStoryDistributionListRecord> {
    Log.d(TAG, "Attempting to get matching record...")
    val matching = SignalDatabase.distributionLists.getRecipientIdForSyncRecord(remote)
    if (matching == null && UuidUtil.parseOrThrow(remote.proto.identifier) == DistributionId.MY_STORY.asUuid()) {
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

      return StorageSyncModels.localToRemoteRecord(recordForSync).let { it.proto.storyDistributionList!!.toSignalStoryDistributionListRecord(it.id) }.asOptional()
    } else {
      Log.d(TAG, "Could not find a matching record. Returning an empty.")
      return Optional.empty()
    }
  }

  override fun merge(remote: SignalStoryDistributionListRecord, local: SignalStoryDistributionListRecord, keyGenerator: StorageKeyGenerator): SignalStoryDistributionListRecord {
    val merged = SignalStoryDistributionListRecord.newBuilder(remote.serializedUnknowns).apply {
      identifier = remote.proto.identifier
      name = remote.proto.name
      recipientServiceIds = remote.proto.recipientServiceIds
      deletedAtTimestamp = remote.proto.deletedAtTimestamp
      allowsReplies = remote.proto.allowsReplies
      isBlockList = remote.proto.isBlockList
      recipientServiceIdsBinary = remote.proto.recipientServiceIdsBinary
    }.build().toSignalStoryDistributionListRecord(StorageId.forStoryDistributionList(keyGenerator.generate()))

    val matchesRemote = doParamsMatch(remote, merged)
    val matchesLocal = doParamsMatch(local, merged)

    return if (matchesRemote) {
      remote
    } else if (matchesLocal) {
      local
    } else {
      merged
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
    return if (o1.proto.identifier == o2.proto.identifier) {
      0
    } else {
      1
    }
  }

  /**
   * Thrown when the RecipientSettings object for a given distribution list is not the
   * correct group type (4).
   */
  private class InvalidGroupTypeException : RuntimeException()

  /**
   * Thrown when we try to ge the matching record for the "My Story" distribution ID but
   * it isn't in the database.
   */
  private class MyStoryDoesNotExistException : RuntimeException()
}
