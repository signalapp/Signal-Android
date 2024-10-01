/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.database

import org.signal.core.util.insertInto
import org.signal.core.util.select
import org.thoughtcrime.securesms.backup.v2.ImportState
import org.thoughtcrime.securesms.backup.v2.proto.AdHocCall
import org.thoughtcrime.securesms.database.CallTable

fun CallTable.getAdhocCallsForBackup(): AdHocCallArchiveExportIterator {
  return AdHocCallArchiveExportIterator(
    readableDatabase
      .select()
      .from(CallTable.TABLE_NAME)
      .where("${CallTable.TYPE} = ?", CallTable.Type.serialize(CallTable.Type.AD_HOC_CALL))
      .run()
  )
}

fun CallTable.restoreCallLogFromBackup(call: AdHocCall, importState: ImportState) {
  val event = when (call.state) {
    AdHocCall.State.GENERIC -> CallTable.Event.GENERIC_GROUP_CALL
    AdHocCall.State.UNKNOWN_STATE -> CallTable.Event.GENERIC_GROUP_CALL
  }

  writableDatabase
    .insertInto(CallTable.TABLE_NAME)
    .values(
      CallTable.CALL_ID to call.callId,
      CallTable.PEER to importState.remoteToLocalRecipientId[call.recipientId]!!.serialize(),
      CallTable.TYPE to CallTable.Type.serialize(CallTable.Type.AD_HOC_CALL),
      CallTable.DIRECTION to CallTable.Direction.serialize(CallTable.Direction.OUTGOING),
      CallTable.EVENT to CallTable.Event.serialize(event),
      CallTable.TIMESTAMP to call.callTimestamp
    )
    .run()
}
