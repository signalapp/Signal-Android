/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

@file:JvmName("RestoreDecisionStateUtil")

package org.thoughtcrime.securesms.keyvalue

import org.thoughtcrime.securesms.database.model.databaseprotos.RestoreDecisionState

/** Are we still awaiting a final decision about restore. */
val RestoreDecisionState.isDecisionPending: Boolean
  get() = when (this.decisionState) {
    RestoreDecisionState.State.START -> true
    RestoreDecisionState.State.INTEND_TO_RESTORE -> true
    RestoreDecisionState.State.NEW_ACCOUNT -> false
    RestoreDecisionState.State.SKIPPED -> false
    RestoreDecisionState.State.COMPLETED -> false
  }

/** Has the user skiped the restore flow and continued on through normal registration. */
val RestoreDecisionState.skippedRestoreChoice: Boolean
  get() = this.decisionState == RestoreDecisionState.State.START

/** Has the user indicated they want a manual remote restore but not via quick restore. */
val RestoreDecisionState.isWantingManualRemoteRestore: Boolean
  get() = when (this.decisionState) {
    RestoreDecisionState.State.INTEND_TO_RESTORE -> {
      this.intendToRestoreData?.fromRemote == true && !this.intendToRestoreData.hasOldDevice
    }
    else -> false
  }

val RestoreDecisionState.includeDeviceToDeviceTransfer: Boolean
  get() = when (this.decisionState) {
    RestoreDecisionState.State.INTEND_TO_RESTORE -> {
      this.intendToRestoreData?.hasOldDevice == true
    }
    else -> true
  }

/** Has a final decision been made regarding restoring. */
val RestoreDecisionState.isTerminal: Boolean
  get() = !isDecisionPending

/** Start of the decision state 'machine'. Should really only be necessary on fresh install first launch. */
val RestoreDecisionState.Companion.Start: RestoreDecisionState
  get() = RestoreDecisionState(RestoreDecisionState.State.START)

/** Helper to create a [RestoreDecisionState.State.INTEND_TO_RESTORE] with appropriate data. */
fun RestoreDecisionState.Companion.intendToRestore(hasOldDevice: Boolean, fromRemote: Boolean?): RestoreDecisionState {
  return RestoreDecisionState(
    decisionState = RestoreDecisionState.State.INTEND_TO_RESTORE,
    intendToRestoreData = RestoreDecisionState.IntendToRestoreData(hasOldDevice = hasOldDevice, fromRemote = fromRemote)
  )
}

/** Terminal decision made for the user if we think this is a registration without a backup */
val RestoreDecisionState.Companion.NewAccount: RestoreDecisionState
  get() = RestoreDecisionState(RestoreDecisionState.State.NEW_ACCOUNT)

/** User elected not to restore any backup. */
val RestoreDecisionState.Companion.Skipped: RestoreDecisionState
  get() = RestoreDecisionState(RestoreDecisionState.State.SKIPPED)

/** User elected to and successful completed restoring data in some form. */
val RestoreDecisionState.Companion.Completed: RestoreDecisionState
  get() = RestoreDecisionState(RestoreDecisionState.State.COMPLETED)
