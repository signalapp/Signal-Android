/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.keyvalue

import org.signal.storage.keyvalue.RegistrationValues
import org.signal.storage.keyvalue.Skipped
import org.signal.storage.keyvalue.isTerminal
import org.signal.storage.protos.RestoreDecisionState
import org.thoughtcrime.securesms.dependencies.AppDependencies

/**
 * App-layer reactions to changes in [SignalStore] values.
 */
object SignalStoreChangeListeners {

  fun register(store: SignalStore) {
    val registration = store.registrationValues

    registration.addChangeListener(RegistrationValues.RESTORE_DECISION_STATE, defaultValue = RestoreDecisionState.Skipped, RestoreDecisionState.ADAPTER) { state ->
      if (state.isTerminal) {
        AppDependencies.incomingMessageObserver.notifyRestoreDecisionMade()
      }
    }
  }
}
