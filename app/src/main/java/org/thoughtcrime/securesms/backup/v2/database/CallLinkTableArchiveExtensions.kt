/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.database

import org.signal.core.util.SqlUtil
import org.signal.core.util.deleteAll
import org.signal.core.util.select
import org.thoughtcrime.securesms.database.CallLinkTable

fun CallLinkTable.getCallLinksForBackup(): CallLinkArchiveExporter {
  val cursor = readableDatabase
    .select()
    .from(CallLinkTable.TABLE_NAME)
    .run()

  return CallLinkArchiveExporter(cursor)
}

fun CallLinkTable.clearAllDataForBackup() {
  writableDatabase.deleteAll(CallLinkTable.TABLE_NAME)
  SqlUtil.resetAutoIncrementValue(writableDatabase, CallLinkTable.TABLE_NAME)
}
