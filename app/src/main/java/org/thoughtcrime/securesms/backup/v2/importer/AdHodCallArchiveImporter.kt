/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.importer

import org.signal.core.util.insertInto
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.backup.v2.ImportState
import org.thoughtcrime.securesms.backup.v2.proto.AdHocCall
import org.thoughtcrime.securesms.database.CallTable
import org.thoughtcrime.securesms.database.SignalDatabase

/**
 * Handles the importing of [AdHocCall] models into the local database.
 */
object AdHodCallArchiveImporter {

  private val TAG = Log.tag(AdHodCallArchiveImporter::class)

  fun import(call: AdHocCall, importState: ImportState) {
    val event = when (call.state) {
      AdHocCall.State.GENERIC -> CallTable.Event.GENERIC_GROUP_CALL
      AdHocCall.State.UNKNOWN_STATE -> CallTable.Event.GENERIC_GROUP_CALL
    }

    val peer = importState.remoteToLocalRecipientId[call.recipientId] ?: run {
      Log.w(TAG, "Failed to find matching recipientId for peer with remote recipientId ${call.recipientId}! Skipping.")
      return
    }

    SignalDatabase.writableDatabase
      .insertInto(CallTable.TABLE_NAME)
      .values(
        CallTable.CALL_ID to call.callId,
        CallTable.PEER to peer.serialize(),
        CallTable.TYPE to CallTable.Type.serialize(CallTable.Type.AD_HOC_CALL),
        CallTable.DIRECTION to CallTable.Direction.serialize(CallTable.Direction.OUTGOING),
        CallTable.EVENT to CallTable.Event.serialize(event),
        CallTable.TIMESTAMP to call.callTimestamp
      )
      .run()
  }
}
