/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.preferences

import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.thoughtcrime.securesms.keyvalue.SignalStore

class AdvancedPinSettingsViewModel : ViewModel() {

  enum class Dialog {
    NONE,
    REGISTRATION_LOCK,
    RECORD_PAYMENTS_RECOVERY_PHRASE
  }

  enum class Event {
    SHOW_OPT_OUT_DIALOG,
    LAUNCH_PIN_CREATION_FLOW,
    LAUNCH_RECOVERY_PHRASE_HANDLING
  }

  private val internalDialog = MutableStateFlow(Dialog.NONE)
  private val internalEvent = MutableSharedFlow<Event>()
  private val internalHasOptedOutOfPin = MutableStateFlow(SignalStore.svr.hasOptedOut())

  val dialog: StateFlow<Dialog> = internalDialog
  val event: SharedFlow<Event> = internalEvent
  val hasOptedOutOfPin: StateFlow<Boolean> = internalHasOptedOutOfPin
  val snackbarHostState = SnackbarHostState()

  fun refresh() {
    internalHasOptedOutOfPin.value = SignalStore.svr.hasOptedOut()
  }

  fun setOptOut(enabled: Boolean) {
    val hasRegistrationLock = SignalStore.svr.isRegistrationLockEnabled

    when {
      !enabled && hasRegistrationLock -> {
        internalDialog.value = Dialog.REGISTRATION_LOCK
      }
      !enabled && SignalStore.payments.mobileCoinPaymentsEnabled() && !SignalStore.payments.userConfirmedMnemonic -> {
        internalDialog.value = Dialog.RECORD_PAYMENTS_RECOVERY_PHRASE
      }
      !enabled -> {
        dismissDialog()
        emitEvent(Event.SHOW_OPT_OUT_DIALOG)
      }
      else -> {
        dismissDialog()
        emitEvent(Event.LAUNCH_PIN_CREATION_FLOW)
      }
    }
  }

  fun launchRecoveryPhraseHandling() {
    emitEvent(Event.LAUNCH_RECOVERY_PHRASE_HANDLING)
  }

  fun onPinOptOutSuccess() {
    internalHasOptedOutOfPin.value = SignalStore.svr.hasOptedOut()
  }

  fun dismissDialog() {
    internalDialog.value = Dialog.NONE
  }

  private fun emitEvent(event: Event) {
    viewModelScope.launch {
      internalEvent.emit(event)
    }
  }
}
