/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.olddevice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.registration.data.QuickRegistrationRepository
import org.thoughtcrime.securesms.registration.olddevice.QuickTransferOldDeviceState
import org.thoughtcrime.securesms.registration.olddevice.preparedevice.PrepareDeviceScreenEvents
import org.thoughtcrime.securesms.registration.olddevice.transferaccount.TransferScreenEvents
import org.whispersystems.signalservice.api.provisioning.RestoreMethod
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

class QuickTransferOldDeviceViewModel(reRegisterUri: String) : ViewModel() {

  companion object {
    private val TAG = Log.tag(QuickTransferOldDeviceViewModel::class)
  }

  private val store: MutableStateFlow<QuickTransferOldDeviceState> = MutableStateFlow(
    QuickTransferOldDeviceState(
      reRegisterUri = reRegisterUri,
      lastBackupTimestamp = SignalStore.backup.lastBackupTime
    )
  )

  val state: StateFlow<QuickTransferOldDeviceState> = store

  private val _backStack: MutableStateFlow<List<TransferAccountRoute>> = MutableStateFlow(listOf(TransferAccountRoute.Transfer))
  val backStack: StateFlow<List<TransferAccountRoute>> = _backStack

  private val finishChannel = Channel<Unit>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  val finishRequests: Flow<Unit> = finishChannel.receiveAsFlow()

  fun goBack() {
    popOrFinish()
  }

  fun onEvent(event: PrepareDeviceScreenEvents) {
    when (event) {
      PrepareDeviceScreenEvents.BackUpNow -> {
        store.update { it.copy(navigateToBackupCreation = true) }
      }
      PrepareDeviceScreenEvents.NavigateBack -> {
        popOrFinish()
      }
      PrepareDeviceScreenEvents.SkipAndContinue -> {
        _backStack.update { listOf(TransferAccountRoute.Transfer) }
        transferAccount()
      }
    }
  }

  fun onEvent(event: TransferScreenEvents) {
    when (event) {
      TransferScreenEvents.ContinueOnOtherDeviceDismiss -> {
        _backStack.update { listOf(TransferAccountRoute.Done) }
      }
      TransferScreenEvents.ErrorDialogDismissed -> {
        store.update { it.copy(reRegisterResult = null) }
      }
      TransferScreenEvents.NavigateBack -> {
        _backStack.update { listOf(TransferAccountRoute.Done) }
      }
      TransferScreenEvents.TransferClicked -> {
        store.update { it.copy(performAuthentication = true) }
      }
    }
  }

  fun onTransferAccountAttempted() {
    val timeSinceLastBackup = (System.currentTimeMillis() - store.value.lastBackupTimestamp).milliseconds
    if (timeSinceLastBackup > 30.minutes) {
      Log.i(TAG, "It's been $timeSinceLastBackup since the last backup. Prompting user to back up now.")
      _backStack.update { it + TransferAccountRoute.PrepareDevice }
    } else {
      Log.i(TAG, "It's been $timeSinceLastBackup since the last backup. We can continue without prompting.")
      transferAccount()
    }
  }

  fun clearAttemptAuthentication() {
    store.update { it.copy(performAuthentication = false) }
  }

  fun clearNavigateToBackupCreation() {
    store.update { it.copy(navigateToBackupCreation = false) }
  }

  private fun popOrFinish() {
    if (_backStack.value.size > 1) {
      _backStack.update { it.dropLast(1) }
    } else {
      finishChannel.trySend(Unit)
    }
  }

  private fun transferAccount() {
    viewModelScope.launch(Dispatchers.IO) {
      val restoreMethodToken = UUID.randomUUID().toString()
      store.update { it.copy(inProgress = true) }
      val result = QuickRegistrationRepository.transferAccount(store.value.reRegisterUri, restoreMethodToken)
      store.update { it.copy(reRegisterResult = result, inProgress = false) }

      if (result == QuickRegistrationRepository.TransferAccountResult.SUCCESS) {
        val restoreMethod = QuickRegistrationRepository.waitForRestoreMethodSelectionOnNewDevice(restoreMethodToken)

        if (restoreMethod != RestoreMethod.DECLINE) {
          SignalStore.Companion.registration.restoringOnNewDevice = true
        }

        store.update { it.copy(restoreMethodSelected = restoreMethod) }
      }
    }
  }
}
