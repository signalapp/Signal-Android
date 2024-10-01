/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.database

import org.signal.core.util.deleteAll
import org.signal.core.util.logging.Log
import org.signal.core.util.select
import org.signal.core.util.withinTransaction
import org.thoughtcrime.securesms.backup.v2.ImportState
import org.thoughtcrime.securesms.backup.v2.exporters.DistributionListArchiveExportIterator
import org.thoughtcrime.securesms.backup.v2.proto.DistributionListItem
import org.thoughtcrime.securesms.database.DistributionListTables
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.DistributionListId
import org.thoughtcrime.securesms.database.model.DistributionListPrivacyMode
import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.signalservice.api.push.DistributionId
import org.whispersystems.signalservice.api.util.UuidUtil
import org.thoughtcrime.securesms.backup.v2.proto.DistributionList as BackupDistributionList

private val TAG = Log.tag(DistributionListTables::class.java)

fun DistributionListTables.getAllForBackup(): DistributionListArchiveExportIterator {
  val cursor = readableDatabase
    .select()
    .from(DistributionListTables.ListTable.TABLE_NAME)
    .run()

  return DistributionListArchiveExportIterator(cursor, this)
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

fun DistributionListTables.restoreFromBackup(dlistItem: DistributionListItem, importState: ImportState): RecipientId? {
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
    .mapNotNull { importState.remoteToLocalRecipientId[it] }

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

private fun BackupDistributionList.PrivacyMode.toLocalPrivacyMode(): DistributionListPrivacyMode {
  return when (this) {
    BackupDistributionList.PrivacyMode.UNKNOWN -> DistributionListPrivacyMode.ALL
    BackupDistributionList.PrivacyMode.ONLY_WITH -> DistributionListPrivacyMode.ONLY_WITH
    BackupDistributionList.PrivacyMode.ALL -> DistributionListPrivacyMode.ALL
    BackupDistributionList.PrivacyMode.ALL_EXCEPT -> DistributionListPrivacyMode.ALL_EXCEPT
  }
}
