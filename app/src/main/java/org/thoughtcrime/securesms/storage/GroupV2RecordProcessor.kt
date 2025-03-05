package org.thoughtcrime.securesms.storage

import org.signal.core.util.logging.Log
import org.signal.libsignal.zkgroup.groups.GroupMasterKey
import org.thoughtcrime.securesms.database.GroupTable
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.RecipientRecord
import org.thoughtcrime.securesms.groups.GroupId
import org.whispersystems.signalservice.api.storage.SignalGroupV2Record
import org.whispersystems.signalservice.api.storage.SignalStorageRecord
import org.whispersystems.signalservice.api.storage.StorageId
import org.whispersystems.signalservice.api.storage.toSignalGroupV2Record
import java.util.Optional

/**
 * Record processor for [SignalGroupV2Record].
 * Handles merging and updating our local store when processing remote gv2 storage records.
 */
class GroupV2RecordProcessor(private val recipientTable: RecipientTable, private val groupDatabase: GroupTable) : DefaultStorageRecordProcessor<SignalGroupV2Record>() {
  companion object {
    private val TAG = Log.tag(GroupV2RecordProcessor::class.java)
  }

  constructor() : this(SignalDatabase.recipients, SignalDatabase.groups)

  override fun isInvalid(remote: SignalGroupV2Record): Boolean {
    return remote.proto.masterKey.size != GroupMasterKey.SIZE
  }

  override fun getMatching(remote: SignalGroupV2Record, keyGenerator: StorageKeyGenerator): Optional<SignalGroupV2Record> {
    val groupId = GroupId.v2(GroupMasterKey(remote.proto.masterKey.toByteArray()))

    val recipientId = recipientTable.getByGroupId(groupId)

    return recipientId
      .map { recipientTable.getRecordForSync(it)!! }
      .map { settings: RecipientRecord ->
        if (settings.syncExtras.groupMasterKey != null) {
          StorageSyncModels.localToRemoteRecord(settings)
        } else {
          Log.w(TAG, "No local master key. Assuming it matches remote since the groupIds match. Enqueuing a fetch to fix the bad state.")
          groupDatabase.fixMissingMasterKey(GroupMasterKey(remote.proto.masterKey.toByteArray()))
          StorageSyncModels.localToRemoteRecord(settings, GroupMasterKey(remote.proto.masterKey.toByteArray()))
        }
      }
      .map { record: SignalStorageRecord -> record.proto.groupV2!!.toSignalGroupV2Record(record.id) }
  }

  override fun merge(remote: SignalGroupV2Record, local: SignalGroupV2Record, keyGenerator: StorageKeyGenerator): SignalGroupV2Record {
    val merged = SignalGroupV2Record.newBuilder(remote.serializedUnknowns).apply {
      masterKey = remote.proto.masterKey
      blocked = remote.proto.blocked
      whitelisted = remote.proto.whitelisted
      archived = remote.proto.archived
      markedUnread = remote.proto.markedUnread
      mutedUntilTimestamp = remote.proto.mutedUntilTimestamp
      dontNotifyForMentionsIfMuted = remote.proto.dontNotifyForMentionsIfMuted
      hideStory = remote.proto.hideStory
      storySendMode = remote.proto.storySendMode
      avatarColor = local.proto.avatarColor
    }.build().toSignalGroupV2Record(StorageId.forGroupV2(keyGenerator.generate()))

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

  override fun insertLocal(record: SignalGroupV2Record) {
    recipientTable.applyStorageSyncGroupV2Insert(record)
  }

  override fun updateLocal(update: StorageRecordUpdate<SignalGroupV2Record>) {
    recipientTable.applyStorageSyncGroupV2Update(update)
  }

  override fun compare(lhs: SignalGroupV2Record, rhs: SignalGroupV2Record): Int {
    return if (lhs.proto.masterKey == rhs.proto.masterKey) {
      0
    } else {
      1
    }
  }
}
