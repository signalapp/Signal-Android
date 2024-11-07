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
import org.whispersystems.signalservice.internal.storage.protos.GroupV2Record
import java.util.Optional

class GroupV2RecordProcessor(private val recipientTable: RecipientTable, private val groupDatabase: GroupTable) : DefaultStorageRecordProcessor<SignalGroupV2Record>() {
  companion object {
    private val TAG = Log.tag(GroupV2RecordProcessor::class.java)
  }

  constructor() : this(SignalDatabase.recipients, SignalDatabase.groups)

  override fun isInvalid(remote: SignalGroupV2Record): Boolean {
    return remote.masterKeyBytes.size != GroupMasterKey.SIZE
  }

  override fun getMatching(remote: SignalGroupV2Record, keyGenerator: StorageKeyGenerator): Optional<SignalGroupV2Record> {
    val groupId = GroupId.v2(remote.masterKeyOrThrow)

    val recipientId = recipientTable.getByGroupId(groupId)

    return recipientId
      .map { recipientTable.getRecordForSync(it)!! }
      .map { settings: RecipientRecord ->
        if (settings.syncExtras.groupMasterKey != null) {
          StorageSyncModels.localToRemoteRecord(settings)
        } else {
          Log.w(TAG, "No local master key. Assuming it matches remote since the groupIds match. Enqueuing a fetch to fix the bad state.")
          groupDatabase.fixMissingMasterKey(remote.masterKeyOrThrow)
          StorageSyncModels.localToRemoteRecord(settings, remote.masterKeyOrThrow)
        }
      }
      .map { record: SignalStorageRecord -> record.groupV2.get() }
  }

  override fun merge(remote: SignalGroupV2Record, local: SignalGroupV2Record, keyGenerator: StorageKeyGenerator): SignalGroupV2Record {
    val unknownFields = remote.serializeUnknownFields()
    val blocked = remote.isBlocked
    val profileSharing = remote.isProfileSharingEnabled
    val archived = remote.isArchived
    val forcedUnread = remote.isForcedUnread
    val muteUntil = remote.muteUntil
    val notifyForMentionsWhenMuted = remote.notifyForMentionsWhenMuted()
    val hideStory = remote.shouldHideStory()
    val storySendMode = remote.storySendMode

    val matchesRemote = doParamsMatch(
      group = remote,
      unknownFields = unknownFields,
      blocked = blocked,
      profileSharing = profileSharing,
      archived = archived,
      forcedUnread = forcedUnread,
      muteUntil = muteUntil,
      notifyForMentionsWhenMuted = notifyForMentionsWhenMuted,
      hideStory = hideStory,
      storySendMode = storySendMode
    )
    val matchesLocal = doParamsMatch(
      group = local,
      unknownFields = unknownFields,
      blocked = blocked,
      profileSharing = profileSharing,
      archived = archived,
      forcedUnread = forcedUnread,
      muteUntil = muteUntil,
      notifyForMentionsWhenMuted = notifyForMentionsWhenMuted,
      hideStory = hideStory,
      storySendMode = storySendMode
    )

    return if (matchesRemote) {
      remote
    } else if (matchesLocal) {
      local
    } else {
      SignalGroupV2Record.Builder(keyGenerator.generate(), remote.masterKeyBytes, unknownFields)
        .setBlocked(blocked)
        .setProfileSharingEnabled(profileSharing)
        .setArchived(archived)
        .setForcedUnread(forcedUnread)
        .setMuteUntil(muteUntil)
        .setNotifyForMentionsWhenMuted(notifyForMentionsWhenMuted)
        .setHideStory(hideStory)
        .setStorySendMode(storySendMode)
        .build()
    }
  }

  override fun insertLocal(record: SignalGroupV2Record) {
    recipientTable.applyStorageSyncGroupV2Insert(record)
  }

  override fun updateLocal(update: StorageRecordUpdate<SignalGroupV2Record>) {
    recipientTable.applyStorageSyncGroupV2Update(update)
  }

  override fun compare(lhs: SignalGroupV2Record, rhs: SignalGroupV2Record): Int {
    return if (lhs.masterKeyBytes.contentEquals(rhs.masterKeyBytes)) {
      0
    } else {
      1
    }
  }

  private fun doParamsMatch(
    group: SignalGroupV2Record,
    unknownFields: ByteArray?,
    blocked: Boolean,
    profileSharing: Boolean,
    archived: Boolean,
    forcedUnread: Boolean,
    muteUntil: Long,
    notifyForMentionsWhenMuted: Boolean,
    hideStory: Boolean,
    storySendMode: GroupV2Record.StorySendMode
  ): Boolean {
    return unknownFields.contentEquals(group.serializeUnknownFields()) &&
      blocked == group.isBlocked &&
      profileSharing == group.isProfileSharingEnabled &&
      archived == group.isArchived &&
      forcedUnread == group.isForcedUnread &&
      muteUntil == group.muteUntil &&
      notifyForMentionsWhenMuted == group.notifyForMentionsWhenMuted() &&
      hideStory == group.shouldHideStory() &&
      storySendMode == group.storySendMode
  }
}
