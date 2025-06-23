/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.backups

import androidx.annotation.WorkerThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.asFlow
import org.signal.core.util.concurrent.SignalDispatchers
import org.signal.core.util.logging.Log
import org.signal.donations.InAppPaymentType
import org.thoughtcrime.securesms.backup.DeletionState
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.util.Environment
import org.thoughtcrime.securesms.util.InternetConnectionObserver
import org.thoughtcrime.securesms.util.RemoteConfig
import kotlin.time.Duration.Companion.milliseconds

class BackupsSettingsViewModel : ViewModel() {

  companion object {
    private val TAG = Log.tag(BackupsSettingsViewModel::class)
  }

  private val internalStateFlow = MutableStateFlow(BackupsSettingsState())

  val stateFlow: StateFlow<BackupsSettingsState> = internalStateFlow

  private val loadRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

  init {
    viewModelScope.launch(SignalDispatchers.Default) {
      InternetConnectionObserver.observe().asFlow()
        .distinctUntilChanged()
        .filter { it }
        .drop(1)
        .collect {
          Log.d(TAG, "Triggering refresh from internet reconnect.")
          loadRequests.emit(Unit)
        }
    }

    viewModelScope.launch(SignalDispatchers.Default) {
      loadRequests.collect {
        Log.d(TAG, "-- Dispatching state load.")
        loadEnabledState().join()
        Log.d(TAG, "-- Completed state load.")
      }
    }

    viewModelScope.launch(SignalDispatchers.Default) {
      InAppPaymentsRepository.observeLatestBackupPayment().collect {
        Log.d(TAG, "Triggering refresh from payment state change.")
        loadRequests.emit(Unit)
      }
    }
  }

  override fun onCleared() {
    Log.d(TAG, "ViewModel has been cleared.")
  }

  fun refreshState() {
    Log.d(TAG, "Refreshing state from manual call.")
    viewModelScope.launch(SignalDispatchers.Default) {
      loadRequests.emit(Unit)
    }
  }

  @WorkerThread
  private fun loadEnabledState(): Job {
    return viewModelScope.launch(SignalDispatchers.IO) {
      if (!RemoteConfig.messageBackups) {
        Log.w(TAG, "Remote backups are not available on this device.")
        internalStateFlow.update { it.copy(backupState = BackupState.NotAvailable, showBackupTierInternalOverride = false) }
      } else {
        val latestPurchase = SignalDatabase.inAppPayments.getLatestInAppPaymentByType(InAppPaymentType.RECURRING_BACKUP)
        val enabledState = BackupStateRepository.resolveBackupState(latestPurchase)

        Log.d(TAG, "Found enabled state $enabledState. Updating UI state.")
        internalStateFlow.update {
          it.copy(
            backupState = enabledState,
            lastBackupAt = SignalStore.backup.lastBackupTime.milliseconds,
            showBackupTierInternalOverride = RemoteConfig.internalUser || Environment.IS_STAGING,
            backupTierInternalOverride = SignalStore.backup.backupTierInternalOverride
          )
        }
      }
    }
  }

  fun onBackupTierInternalOverrideChanged(tier: MessageBackupTier?) {
    SignalStore.backup.backupTierInternalOverride = tier
    SignalStore.backup.deletionState = DeletionState.NONE
    viewModelScope.launch(SignalDispatchers.Default) {
      SignalDatabase.recipients.markNeedsSync(Recipient.self().id)
      StorageSyncHelper.scheduleSyncForDataChange()
    }
    refreshState()
  }
}
