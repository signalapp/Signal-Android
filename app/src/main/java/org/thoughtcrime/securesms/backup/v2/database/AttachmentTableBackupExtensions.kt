/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.database

import org.signal.core.util.deleteAll
import org.thoughtcrime.securesms.database.AttachmentTable

fun AttachmentTable.clearAllDataForBackupRestore() {
  writableDatabase.deleteAll(AttachmentTable.TABLE_NAME)
}
