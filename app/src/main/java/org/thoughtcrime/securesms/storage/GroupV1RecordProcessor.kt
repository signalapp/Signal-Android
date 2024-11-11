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
import org.whispersystems.signalservice.api.storage.toSignalGroupV1Record
import java.util.Optional

/**
 * Handles merging remote storage updates into local group v1 state.
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
      val id = GroupId.v1(remote.groupId)
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
    val groupId = GroupId.v1orThrow(remote.groupId)

    val recipientId = recipientTable.getByGroupId(groupId)

    return recipientId
      .map { recipientTable.getRecordForSync(it)!! }
      .map { settings: RecipientRecord -> StorageSyncModels.localToRemoteRecord(settings) }
      .map { record: SignalStorageRecord -> record.proto.groupV1!!.toSignalGroupV1Record(record.id) }
  }

  override fun merge(remote: SignalGroupV1Record, local: SignalGroupV1Record, keyGenerator: StorageKeyGenerator): SignalGroupV1Record {
    val unknownFields = remote.serializeUnknownFields()
    val blocked = remote.isBlocked
    val profileSharing = remote.isProfileSharingEnabled
    val archived = remote.isArchived
    val forcedUnread = remote.isForcedUnread
    val muteUntil = remote.muteUntil

    val matchesRemote = doParamsMatch(group = remote, unknownFields = unknownFields, blocked = blocked, profileSharing = profileSharing, archived = archived, forcedUnread = forcedUnread, muteUntil = muteUntil)
    val matchesLocal = doParamsMatch(group = local, unknownFields = unknownFields, blocked = blocked, profileSharing = profileSharing, archived = archived, forcedUnread = forcedUnread, muteUntil = muteUntil)

    return if (matchesRemote) {
      remote
    } else if (matchesLocal) {
      local
    } else {
      SignalGroupV1Record.Builder(keyGenerator.generate(), remote.groupId, unknownFields)
        .setBlocked(blocked)
        .setProfileSharingEnabled(profileSharing)
        .setArchived(archived)
        .setForcedUnread(forcedUnread)
        .setMuteUntil(muteUntil)
        .build()
    }
  }

  override fun insertLocal(record: SignalGroupV1Record) {
    recipientTable.applyStorageSyncGroupV1Insert(record)
  }

  override fun updateLocal(update: StorageRecordUpdate<SignalGroupV1Record>) {
    recipientTable.applyStorageSyncGroupV1Update(update)
  }

  override fun compare(lhs: SignalGroupV1Record, rhs: SignalGroupV1Record): Int {
    return if (lhs.groupId.contentEquals(rhs.groupId)) {
      0
    } else {
      1
    }
  }

  private fun doParamsMatch(
    group: SignalGroupV1Record,
    unknownFields: ByteArray?,
    blocked: Boolean,
    profileSharing: Boolean,
    archived: Boolean,
    forcedUnread: Boolean,
    muteUntil: Long
  ): Boolean {
    return unknownFields.contentEquals(group.serializeUnknownFields()) &&
      blocked == group.isBlocked &&
      profileSharing == group.isProfileSharingEnabled &&
      archived == group.isArchived &&
      forcedUnread == group.isForcedUnread &&
      muteUntil == group.muteUntil
  }
}
