/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.processor

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.backup.v2.BackupState
import org.thoughtcrime.securesms.backup.v2.database.getCallsForBackup
import org.thoughtcrime.securesms.backup.v2.database.restoreCallLogFromBackup
import org.thoughtcrime.securesms.backup.v2.proto.Frame
import org.thoughtcrime.securesms.backup.v2.stream.BackupFrameEmitter
import org.thoughtcrime.securesms.database.SignalDatabase

typealias BackupCall = org.thoughtcrime.securesms.backup.v2.proto.Call

object CallLogBackupProcessor {

  val TAG = Log.tag(CallLogBackupProcessor::class.java)

  fun export(emitter: BackupFrameEmitter) {
    SignalDatabase.calls.getCallsForBackup().use { reader ->
      for (callLog in reader) {
        if (callLog != null) {
          emitter.emit(Frame(call = callLog))
        }
      }
    }
  }

  fun import(call: BackupCall, backupState: BackupState) {
    SignalDatabase.calls.restoreCallLogFromBackup(call, backupState)
  }
}
