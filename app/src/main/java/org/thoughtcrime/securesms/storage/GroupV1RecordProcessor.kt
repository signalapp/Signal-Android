package org.thoughtcrime.securesms.storage

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.GroupTable
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.RecipientRecord
import org.thoughtcrime.securesms.groups.BadGroupIdException
import org.thoughtcrime.securesms.groups.GroupId
import org.whispersystems.signalservice.api.storage.SignalGroupV1Record
import org.whispersystems.signalservice.api.storage.SignalStorageRecord
import org.whispersystems.signalservice.api.storage.StorageId
import org.whispersystems.signalservice.api.storage.toSignalGroupV1Record
import java.util.Optional

/**
 * Record processor for [SignalGroupV1Record].
 * Handles merging and updating our local store when processing remote gv1 storage records.
 */
class GroupV1RecordProcessor(private val groupDatabase: GroupTable, private val recipientTable: RecipientTable) : DefaultStorageRecordProcessor<SignalGroupV1Record>() {
  companion object {
    private val TAG = Log.tag(GroupV1RecordProcessor::class.java)
  }

  constructor() : this(SignalDatabase.groups, SignalDatabase.recipients)

  /**
   * We want to catch:
   * - Invalid group ID's
   * - GV1 ID's that map to GV2 ID's, meaning we've already migrated them.
   *
   * Note: This method could be written more succinctly, but the logs are useful :)
   */
  override fun isInvalid(remote: SignalGroupV1Record): Boolean {
    try {
      val id = GroupId.v1(remote.proto.id.toByteArray())
      val v2Record = groupDatabase.getGroup(id.deriveV2MigrationGroupId())

      if (v2Record.isPresent) {
        Log.w(TAG, "We already have an upgraded V2 group for this V1 group -- marking as invalid.")
        return true
      } else {
        return false
      }
    } catch (e: BadGroupIdException) {
      Log.w(TAG, "Bad Group ID -- marking as invalid.")
      return true
    }
  }

  override fun getMatching(remote: SignalGroupV1Record, keyGenerator: StorageKeyGenerator): Optional<SignalGroupV1Record> {
    val groupId = GroupId.v1orThrow(remote.proto.id.toByteArray())

    val recipientId = recipientTable.getByGroupId(groupId)

    return recipientId
      .map { recipientTable.getRecordForSync(it)!! }
      .map { settings: RecipientRecord -> StorageSyncModels.localToRemoteRecord(settings) }
      .map { record: SignalStorageRecord -> record.proto.groupV1!!.toSignalGroupV1Record(record.id) }
  }

  override fun merge(remote: SignalGroupV1Record, local: SignalGroupV1Record, keyGenerator: StorageKeyGenerator): SignalGroupV1Record {
    val merged = SignalGroupV1Record.newBuilder(remote.serializedUnknowns).apply {
      id = remote.proto.id
      blocked = remote.proto.blocked
      whitelisted = remote.proto.whitelisted
      archived = remote.proto.archived
      markedUnread = remote.proto.markedUnread
      mutedUntilTimestamp = remote.proto.mutedUntilTimestamp
    }.build().toSignalGroupV1Record(StorageId.forGroupV1(keyGenerator.generate()))

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

  override fun insertLocal(record: SignalGroupV1Record) {
    recipientTable.applyStorageSyncGroupV1Insert(record)
  }

  override fun updateLocal(update: StorageRecordUpdate<SignalGroupV1Record>) {
    recipientTable.applyStorageSyncGroupV1Update(update)
  }

  override fun compare(lhs: SignalGroupV1Record, rhs: SignalGroupV1Record): Int {
    return if (lhs.proto.id == rhs.proto.id) {
      0
    } else {
      1
    }
  }
}
