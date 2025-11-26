/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.restore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.core.util.logging.Log
import org.signal.registration.proto.RegistrationProvisionMessage
import org.thoughtcrime.securesms.database.model.databaseprotos.RestoreDecisionState
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.keyvalue.Skipped
import org.thoughtcrime.securesms.registration.data.QuickRegistrationRepository
import org.thoughtcrime.securesms.registration.data.network.RegisterAccountResult
import org.whispersystems.signalservice.api.provisioning.RestoreMethod

class NoBackupToRestoreViewModel(decode: RegistrationProvisionMessage) : ViewModel() {
  companion object {
    private val TAG = Log.tag(NoBackupToRestoreViewModel::class)
  }

  private val store: MutableStateFlow<NoBackupToRestoreState> = MutableStateFlow(NoBackupToRestoreState(provisioningMessage = decode))

  val state: StateFlow<NoBackupToRestoreState> = store

  fun skipRestoreAndRegister() {
    SignalStore.registration.restoreDecisionState = RestoreDecisionState.Skipped
    store.update { it.copy(isRegistering = true) }

    viewModelScope.launch(Dispatchers.IO) {
      QuickRegistrationRepository.setRestoreMethodForOldDevice(RestoreMethod.DECLINE)
    }
  }

  fun handleRegistrationFailure(registerAccountResult: RegisterAccountResult) {
    store.update {
      if (it.isRegistering) {
        Log.w(TAG, "Unable to register [${registerAccountResult::class.simpleName}]", registerAccountResult.getCause(), true)
        it.copy(
          isRegistering = false,
          showRegistrationError = true,
          registerAccountResult = registerAccountResult
        )
      } else {
        it
      }
    }
  }

  fun clearRegistrationError() {
    store.update {
      it.copy(
        showRegistrationError = false,
        registerAccountResult = null
      )
    }
  }

  data class NoBackupToRestoreState(
    val isRegistering: Boolean = false,
    val provisioningMessage: RegistrationProvisionMessage,
    val showRegistrationError: Boolean = false,
    val registerAccountResult: RegisterAccountResult? = null
  )
}
