/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.database

import org.signal.core.util.deleteAll
import org.thoughtcrime.securesms.database.InAppPaymentTable

fun InAppPaymentTable.clearAllDataForBackupRestore() {
  writableDatabase.deleteAll(InAppPaymentTable.TABLE_NAME)
}
