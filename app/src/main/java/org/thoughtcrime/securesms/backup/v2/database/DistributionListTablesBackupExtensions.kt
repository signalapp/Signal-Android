/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.database

import okio.ByteString.Companion.toByteString
import org.signal.core.util.deleteAll
import org.signal.core.util.logging.Log
import org.signal.core.util.readToList
import org.signal.core.util.requireBoolean
import org.signal.core.util.requireLong
import org.signal.core.util.requireNonNullString
import org.signal.core.util.requireObject
import org.signal.core.util.select
import org.signal.core.util.withinTransaction
import org.thoughtcrime.securesms.backup.v2.BackupState
import org.thoughtcrime.securesms.backup.v2.proto.DistributionList
import org.thoughtcrime.securesms.backup.v2.proto.DistributionListItem
import org.thoughtcrime.securesms.database.DistributionListTables
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.DistributionListId
import org.thoughtcrime.securesms.database.model.DistributionListPrivacyMode
import org.thoughtcrime.securesms.database.model.DistributionListRecord
import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.signalservice.api.push.DistributionId
import org.whispersystems.signalservice.api.util.UuidUtil
import org.whispersystems.signalservice.api.util.toByteArray
import org.thoughtcrime.securesms.backup.v2.proto.DistributionList as BackupDistributionList

private val TAG = Log.tag(DistributionListTables::class.java)

data class DistributionRecipient(val id: RecipientId, val record: DistributionListRecord)

fun DistributionListTables.getAllForBackup(): List<BackupRecipient> {
  val records = readableDatabase
    .select()
    .from(DistributionListTables.ListTable.TABLE_NAME)
    .run()
    .readToList { cursor ->
      val id: DistributionListId = DistributionListId.from(cursor.requireLong(DistributionListTables.ListTable.ID))
      val privacyMode: DistributionListPrivacyMode = cursor.requireObject(DistributionListTables.ListTable.PRIVACY_MODE, DistributionListPrivacyMode.Serializer)
      val recipientId: RecipientId = RecipientId.from(cursor.requireLong(DistributionListTables.ListTable.RECIPIENT_ID))
      DistributionRecipient(
        id = recipientId,
        record = DistributionListRecord(
          id = id,
          name = cursor.requireNonNullString(DistributionListTables.ListTable.NAME),
          distributionId = DistributionId.from(cursor.requireNonNullString(DistributionListTables.ListTable.DISTRIBUTION_ID)),
          allowsReplies = cursor.requireBoolean(DistributionListTables.ListTable.ALLOWS_REPLIES),
          rawMembers = getRawMembers(id, privacyMode),
          members = getMembersForBackup(id),
          deletedAtTimestamp = cursor.requireLong(DistributionListTables.ListTable.DELETION_TIMESTAMP),
          isUnknown = cursor.requireBoolean(DistributionListTables.ListTable.IS_UNKNOWN),
          privacyMode = privacyMode
        )
      )
    }

  return records
    .map { recipient ->
      BackupRecipient(
        id = recipient.id.toLong(),
        distributionList = if (recipient.record.deletedAtTimestamp != 0L) {
          DistributionListItem(
            distributionId = recipient.record.distributionId.asUuid().toByteArray().toByteString(),
            deletionTimestamp = recipient.record.deletedAtTimestamp
          )
        } else {
          DistributionListItem(
            distributionId = recipient.record.distributionId.asUuid().toByteArray().toByteString(),
            distributionList = DistributionList(
              name = recipient.record.name,
              allowReplies = recipient.record.allowsReplies,
              privacyMode = recipient.record.privacyMode.toBackupPrivacyMode(),
              memberRecipientIds = recipient.record.members.map { it.toLong() }
            )
          )
        }
      )
    }
}

fun DistributionListTables.getMembersForBackup(id: DistributionListId): List<RecipientId> {
  lateinit var privacyMode: DistributionListPrivacyMode
  lateinit var rawMembers: List<RecipientId>

  readableDatabase.withinTransaction {
    privacyMode = getPrivacyMode(id)
    rawMembers = getRawMembers(id, privacyMode)
  }

  return when (privacyMode) {
    DistributionListPrivacyMode.ALL -> emptyList()
    DistributionListPrivacyMode.ONLY_WITH -> rawMembers
    DistributionListPrivacyMode.ALL_EXCEPT -> rawMembers
  }
}

fun DistributionListTables.restoreFromBackup(dlistItem: DistributionListItem, backupState: BackupState): RecipientId? {
  if (dlistItem.deletionTimestamp != null && dlistItem.deletionTimestamp > 0) {
    val dlistId = createList(
      name = "",
      members = emptyList(),
      distributionId = DistributionId.from(UuidUtil.fromByteString(dlistItem.distributionId)),
      allowsReplies = false,
      deletionTimestamp = dlistItem.deletionTimestamp,
      storageId = null,
      privacyMode = DistributionListPrivacyMode.ONLY_WITH
    )!!

    return SignalDatabase.distributionLists.getRecipientId(dlistId)!!
  }

  val dlist = dlistItem.distributionList ?: return null
  val members: List<RecipientId> = dlist.memberRecipientIds
    .mapNotNull { backupState.backupToLocalRecipientId[it] }

  if (members.size != dlist.memberRecipientIds.size) {
    Log.w(TAG, "Couldn't find some member recipients! Missing backup recipientIds: ${dlist.memberRecipientIds.toSet() - members.toSet()}")
  }

  val distributionId = DistributionId.from(UuidUtil.fromByteString(dlistItem.distributionId))
  val privacyMode = dlist.privacyMode.toLocalPrivacyMode()

  val dlistId = createList(
    name = dlist.name,
    members = members,
    distributionId = distributionId,
    allowsReplies = dlist.allowReplies,
    deletionTimestamp = dlistItem.deletionTimestamp ?: 0,
    storageId = null,
    privacyMode = privacyMode
  )!!

  return SignalDatabase.distributionLists.getRecipientId(dlistId)!!
}

fun DistributionListTables.clearAllDataForBackupRestore() {
  writableDatabase.deleteAll(DistributionListTables.ListTable.TABLE_NAME)
  writableDatabase.deleteAll(DistributionListTables.MembershipTable.TABLE_NAME)
}

private fun DistributionListPrivacyMode.toBackupPrivacyMode(): BackupDistributionList.PrivacyMode {
  return when (this) {
    DistributionListPrivacyMode.ONLY_WITH -> BackupDistributionList.PrivacyMode.ONLY_WITH
    DistributionListPrivacyMode.ALL -> BackupDistributionList.PrivacyMode.ALL
    DistributionListPrivacyMode.ALL_EXCEPT -> BackupDistributionList.PrivacyMode.ALL_EXCEPT
  }
}

private fun BackupDistributionList.PrivacyMode.toLocalPrivacyMode(): DistributionListPrivacyMode {
  return when (this) {
    BackupDistributionList.PrivacyMode.UNKNOWN -> DistributionListPrivacyMode.ALL
    BackupDistributionList.PrivacyMode.ONLY_WITH -> DistributionListPrivacyMode.ONLY_WITH
    BackupDistributionList.PrivacyMode.ALL -> DistributionListPrivacyMode.ALL
    BackupDistributionList.PrivacyMode.ALL_EXCEPT -> DistributionListPrivacyMode.ALL_EXCEPT
  }
}
