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
import org.signal.core.util.money.FiatMoney
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsType
import org.thoughtcrime.securesms.components.settings.app.subscription.RecurringInAppPaymentRepository
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.InternetConnectionObserver
import org.thoughtcrime.securesms.util.RemoteConfig
import java.util.Currency
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

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
          loadRequests.tryEmit(Unit)
        }
    }

    viewModelScope.launch(SignalDispatchers.Default) {
      loadRequests.collect {
        Log.d(TAG, "-- Dispatching state load.")
        loadEnabledState().join()
        Log.d(TAG, "-- Completed state load.")
      }
    }
  }

  override fun onCleared() {
    Log.d(TAG, "ViewModel has been cleared.")
  }

  fun refreshState() {
    Log.d(TAG, "Refreshing state from manual call.")
    loadRequests.tryEmit(Unit)
  }

  @WorkerThread
  private fun loadEnabledState(): Job {
    return viewModelScope.launch(SignalDispatchers.IO) {
      if (!RemoteConfig.messageBackups || !AppDependencies.billingApi.isApiAvailable()) {
        Log.w(TAG, "Paid backups are not available on this device.")
        internalStateFlow.update { it.copy(enabledState = BackupsSettingsState.EnabledState.NotAvailable, showBackupTierInternalOverride = false) }
      } else {
        val enabledState = when (SignalStore.backup.backupTier) {
          MessageBackupTier.FREE -> getEnabledStateForFreeTier()
          MessageBackupTier.PAID -> getEnabledStateForPaidTier()
          null -> getEnabledStateForNoTier()
        }

        Log.d(TAG, "Found enabled state $enabledState. Updating UI state.")
        internalStateFlow.update { it.copy(enabledState = enabledState, showBackupTierInternalOverride = RemoteConfig.internalUser, backupTierInternalOverride = SignalStore.backup.backupTierInternalOverride) }
      }
    }
  }

  fun onBackupTierInternalOverrideChanged(tier: MessageBackupTier?) {
    SignalStore.backup.backupTierInternalOverride = tier
    refreshState()
  }

  private suspend fun getEnabledStateForFreeTier(): BackupsSettingsState.EnabledState {
    return try {
      Log.d(TAG, "Attempting to grab enabled state for free tier.")
      val backupType = BackupRepository.getBackupsType(MessageBackupTier.FREE)!!

      Log.d(TAG, "Retrieved backup type. Returning active state...")
      BackupsSettingsState.EnabledState.Active(
        expiresAt = 0.seconds,
        lastBackupAt = SignalStore.backup.lastBackupTime.milliseconds,
        type = backupType
      )
    } catch (e: Exception) {
      Log.w(TAG, "Failed to build enabled state.", e)
      BackupsSettingsState.EnabledState.Failed
    }
  }

  private suspend fun getEnabledStateForPaidTier(): BackupsSettingsState.EnabledState {
    return try {
      Log.d(TAG, "Attempting to grab enabled state for paid tier.")
      val backupType = BackupRepository.getBackupsType(MessageBackupTier.PAID) as MessageBackupsType.Paid

      Log.d(TAG, "Retrieved backup type. Grabbing active subscription...")
      val activeSubscription = RecurringInAppPaymentRepository.getActiveSubscriptionSync(InAppPaymentSubscriberRecord.Type.BACKUP).getOrThrow()

      Log.d(TAG, "Retrieved subscription. Active? ${activeSubscription.isActive}")
      if (activeSubscription.isActive) {
        BackupsSettingsState.EnabledState.Active(
          expiresAt = activeSubscription.activeSubscription.endOfCurrentPeriod.seconds,
          lastBackupAt = SignalStore.backup.lastBackupTime.milliseconds,
          type = MessageBackupsType.Paid(
            pricePerMonth = FiatMoney.fromSignalNetworkAmount(
              activeSubscription.activeSubscription.amount,
              Currency.getInstance(activeSubscription.activeSubscription.currency)
            ),
            storageAllowanceBytes = backupType.storageAllowanceBytes,
            mediaTtl = backupType.mediaTtl
          )
        )
      } else {
        BackupsSettingsState.EnabledState.Inactive
      }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to build enabled state.", e)
      BackupsSettingsState.EnabledState.Failed
    }
  }

  private fun getEnabledStateForNoTier(): BackupsSettingsState.EnabledState {
    Log.d(TAG, "Grabbing enabled state for no tier.")
    return if (SignalStore.uiHints.hasEverEnabledRemoteBackups) {
      BackupsSettingsState.EnabledState.Inactive
    } else {
      BackupsSettingsState.EnabledState.Never
    }
  }
}
