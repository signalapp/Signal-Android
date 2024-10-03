/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.database

import org.signal.core.util.SqlUtil
import org.signal.core.util.deleteAll
import org.thoughtcrime.securesms.database.ReactionTable

fun ReactionTable.clearAllDataForBackupRestore() {
  writableDatabase.deleteAll(ReactionTable.TABLE_NAME)
  SqlUtil.resetAutoIncrementValue(writableDatabase, ReactionTable.TABLE_NAME)
}
