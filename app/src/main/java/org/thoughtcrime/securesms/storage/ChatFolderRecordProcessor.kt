package org.thoughtcrime.securesms.storage

import org.signal.core.util.SqlUtil
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.components.settings.app.chats.folders.ChatFolderId
import org.thoughtcrime.securesms.database.ChatFolderTables
import org.thoughtcrime.securesms.database.SignalDatabase
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.storage.SignalChatFolderRecord
import org.whispersystems.signalservice.api.storage.StorageId
import org.whispersystems.signalservice.api.util.OptionalUtil.asOptional
import org.whispersystems.signalservice.api.util.UuidUtil
import org.whispersystems.signalservice.internal.storage.protos.ChatFolderRecord
import java.util.Optional
import java.util.UUID

/**
 * Record processor for [SignalChatFolderRecord].
 * Handles merging and updating our local store when processing remote chat folder storage records.
 */
class ChatFolderRecordProcessor : DefaultStorageRecordProcessor<SignalChatFolderRecord>() {

  companion object {
    private val TAG = Log.tag(ChatFolderRecordProcessor::class)
  }

  override fun compare(o1: SignalChatFolderRecord, o2: SignalChatFolderRecord): Int {
    return if (o1.proto.identifier == o2.proto.identifier) {
      0
    } else {
      1
    }
  }

  /**
   * Folders must have a valid identifier and known folder type
   * Custom chat folders must have a name.
   * If a folder is deleted, it must have a -1 position
   * If a folder is not deleted, it must have a non-negative position
   * All recipients must have a valid serviceId
   */
  override fun isInvalid(remote: SignalChatFolderRecord): Boolean {
    return UuidUtil.parseOrNull(remote.proto.identifier) == null ||
      remote.proto.folderType == ChatFolderRecord.FolderType.UNKNOWN ||
      (remote.proto.folderType == ChatFolderRecord.FolderType.CUSTOM && remote.proto.name.isEmpty()) ||
      (remote.proto.deletedAtTimestampMs > 0 && remote.proto.position != -1) ||
      (remote.proto.deletedAtTimestampMs == 0L && remote.proto.position < 0) ||
      containsInvalidServiceId(remote.proto.includedRecipients) ||
      containsInvalidServiceId(remote.proto.excludedRecipients)
  }

  override fun getMatching(remote: SignalChatFolderRecord, keyGenerator: StorageKeyGenerator): Optional<SignalChatFolderRecord> {
    Log.d(TAG, "Attempting to get matching record...")
    val uuid: UUID = UuidUtil.parseOrThrow(remote.proto.identifier)
    val query = SqlUtil.buildQuery("${ChatFolderTables.ChatFolderTable.CHAT_FOLDER_ID} = ?", ChatFolderId.from(uuid))
    val folder = SignalDatabase.chatFolders.getChatFolder(query)

    return if (folder?.storageServiceId != null) {
      StorageSyncModels.localToRemoteChatFolder(folder, folder.storageServiceId.raw).asOptional()
    } else if (folder != null) {
      Log.d(TAG, "Folder was missing a storage service id, generating one")
      val storageId = StorageId.forChatFolder(keyGenerator.generate())
      SignalDatabase.chatFolders.applyStorageIdUpdate(folder.chatFolderId, storageId)
      StorageSyncModels.localToRemoteChatFolder(folder, storageId.raw).asOptional()
    } else {
      Log.d(TAG, "Could not find a matching record. Returning an empty.")
      Optional.empty<SignalChatFolderRecord>()
    }
  }

  /**
   * A deleted record takes precedence over a non-deleted record
   * while an earlier deletion takes precedence over a later deletion
   */
  override fun merge(remote: SignalChatFolderRecord, local: SignalChatFolderRecord, keyGenerator: StorageKeyGenerator): SignalChatFolderRecord {
    return if (remote.proto.deletedAtTimestampMs > 0 && local.proto.deletedAtTimestampMs > 0) {
      if (remote.proto.deletedAtTimestampMs < local.proto.deletedAtTimestampMs) {
        remote
      } else {
        local
      }
    } else if (remote.proto.deletedAtTimestampMs > 0) {
      remote
    } else if (local.proto.deletedAtTimestampMs > 0) {
      local
    } else {
      remote
    }
  }

  override fun insertLocal(record: SignalChatFolderRecord) {
    SignalDatabase.chatFolders.insertChatFolderFromStorageSync(record)
  }

  override fun updateLocal(update: StorageRecordUpdate<SignalChatFolderRecord>) {
    SignalDatabase.chatFolders.updateChatFolderFromStorageSync(update.new)
  }

  private fun containsInvalidServiceId(recipients: List<ChatFolderRecord.Recipient>): Boolean {
    return recipients.any { recipient ->
      recipient.contact != null && ServiceId.parseOrNull(recipient.contact!!.serviceId) == null
    }
  }
}
