/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.database

import org.signal.core.util.select
import org.thoughtcrime.securesms.database.CallTable

fun CallTable.getAdhocCallsForBackup(): AdHocCallArchiveExporter {
  return AdHocCallArchiveExporter(
    readableDatabase
      .select()
      .from(CallTable.TABLE_NAME)
      .where("${CallTable.TYPE} = ?", CallTable.Type.serialize(CallTable.Type.AD_HOC_CALL))
      .run()
  )
}
