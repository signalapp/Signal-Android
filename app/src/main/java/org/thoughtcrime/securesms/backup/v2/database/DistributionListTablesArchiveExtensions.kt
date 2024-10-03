/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.database

import org.signal.core.util.SqlUtil
import org.signal.core.util.deleteAll
import org.signal.core.util.select
import org.signal.core.util.withinTransaction
import org.thoughtcrime.securesms.backup.v2.exporters.DistributionListArchiveExporter
import org.thoughtcrime.securesms.database.DistributionListTables
import org.thoughtcrime.securesms.database.model.DistributionListId
import org.thoughtcrime.securesms.database.model.DistributionListPrivacyMode
import org.thoughtcrime.securesms.recipients.RecipientId

fun DistributionListTables.getAllForBackup(): DistributionListArchiveExporter {
  val cursor = readableDatabase
    .select()
    .from(DistributionListTables.ListTable.TABLE_NAME)
    .run()

  return DistributionListArchiveExporter(cursor, this)
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

fun DistributionListTables.clearAllDataForBackupRestore() {
  writableDatabase.deleteAll(DistributionListTables.ListTable.TABLE_NAME)
  writableDatabase.deleteAll(DistributionListTables.MembershipTable.TABLE_NAME)
  SqlUtil.resetAutoIncrementValue(writableDatabase, DistributionListTables.ListTable.TABLE_NAME)
  SqlUtil.resetAutoIncrementValue(writableDatabase, DistributionListTables.MembershipTable.TABLE_NAME)
}
