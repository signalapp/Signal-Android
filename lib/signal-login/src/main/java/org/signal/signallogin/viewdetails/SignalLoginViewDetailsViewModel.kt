/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.signallogin.viewdetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.signal.core.models.AccountEntropyPool
import org.signal.core.models.ServiceId
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log

/**
 * View model for [SignalLoginViewDetailsScreen].
 *
 * The screen only renders the credentials it is given, so the state is fully derived from the constructor arguments.
 * None of the actions the screen can produce are implemented yet -- every event is routed here and handled explicitly
 * so that filling in the business logic is a matter of replacing the TODO branches.
 */
class SignalLoginViewDetailsViewModel(
  aci: ServiceId.ACI,
  aep: AccountEntropyPool
) : EventDrivenViewModel<SignalLoginViewDetailsScreenEvents>(TAG) {

  companion object {
    private val TAG = Log.tag(SignalLoginViewDetailsViewModel::class)
  }

  private val _state = MutableStateFlow(
    SignalLoginViewDetailsState(
      accountKey = aci.toString().uppercase(),
      recoveryKey = aep.displayValue
    )
  )
  val state: StateFlow<SignalLoginViewDetailsState> = _state.asStateFlow()

  init {
    _state
      .onEach { Log.d(TAG, "[State] $it") }
      .launchIn(viewModelScope)
  }

  override suspend fun processEvent(event: SignalLoginViewDetailsScreenEvents) {
    when (event) {
      is SignalLoginViewDetailsScreenEvents.BackClicked -> {
        // TODO [phonenumberless] Navigate back once this screen is hooked into a flow.
        Log.i(TAG, "Back clicked, but navigation isn't implemented yet.")
      }

      is SignalLoginViewDetailsScreenEvents.SaveToPasswordManagerClicked -> {
        // TODO [phonenumberless] Store the credentials via the credential manager.
        Log.i(TAG, "Save to password manager clicked, but the flow isn't implemented yet.")
      }

      is SignalLoginViewDetailsScreenEvents.SaveAsPdfClicked -> {
        // TODO [phonenumberless] Render the credentials to a PDF and hand it to the user.
        Log.i(TAG, "Save as PDF clicked, but the flow isn't implemented yet.")
      }

      is SignalLoginViewDetailsScreenEvents.AccountIdLongClicked -> {
        // TODO [phonenumberless] Copy the account key to the clipboard.
        Log.i(TAG, "Account key long clicked, but the copy flow isn't implemented yet.")
      }

      is SignalLoginViewDetailsScreenEvents.RecoveryKeyLongClicked -> {
        // TODO [phonenumberless] Copy the recovery key to the clipboard.
        Log.i(TAG, "Recovery key long clicked, but the copy flow isn't implemented yet.")
      }
    }
  }

  class Factory(
    private val aci: ServiceId.ACI,
    private val aep: AccountEntropyPool
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return SignalLoginViewDetailsViewModel(aci, aep) as T
    }
  }
}
