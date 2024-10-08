/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.backups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.asFlow
import org.signal.core.util.logging.Log
import org.signal.core.util.money.FiatMoney
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsType
import org.thoughtcrime.securesms.components.settings.app.subscription.RecurringInAppPaymentRepository
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.InternetConnectionObserver
import java.util.Currency
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class BackupsSettingsViewModel : ViewModel() {

  companion object {
    private val TAG = Log.tag(BackupsSettingsViewModel::class)
  }

  private val internalStateFlow = MutableStateFlow(BackupsSettingsState())

  val stateFlow: StateFlow<BackupsSettingsState> = internalStateFlow

  init {
    viewModelScope.launch(Dispatchers.Default) {
      InternetConnectionObserver.observe().asFlow()
        .distinctUntilChanged()
        .filter { it }
        .drop(1)
        .collect {
          refreshState()
        }
    }
  }

  fun refreshState() {
    Log.d(TAG, "Refreshing state.")
    loadEnabledState()
  }

  private fun loadEnabledState() {
    viewModelScope.launch(Dispatchers.IO) {
      val enabledState = when (SignalStore.backup.backupTier) {
        MessageBackupTier.FREE -> getEnabledStateForFreeTier()
        MessageBackupTier.PAID -> getEnabledStateForPaidTier()
        null -> getEnabledStateForNoTier()
      }

      internalStateFlow.update { it.copy(enabledState = enabledState) }
    }
  }

  private suspend fun getEnabledStateForFreeTier(): BackupsSettingsState.EnabledState {
    return try {
      BackupsSettingsState.EnabledState.Active(
        expiresAt = 0.seconds,
        lastBackupAt = SignalStore.backup.lastBackupTime.milliseconds,
        type = BackupRepository.getBackupsType(MessageBackupTier.FREE)!!
      )
    } catch (e: Exception) {
      Log.w(TAG, "Failed to build enabled state.", e)
      BackupsSettingsState.EnabledState.Failed
    }
  }

  private suspend fun getEnabledStateForPaidTier(): BackupsSettingsState.EnabledState {
    return try {
      val backupType = BackupRepository.getBackupsType(MessageBackupTier.PAID) as MessageBackupsType.Paid
      val activeSubscription = RecurringInAppPaymentRepository.getActiveSubscriptionSync(InAppPaymentSubscriberRecord.Type.BACKUP).getOrThrow()
      if (activeSubscription.isActive) {
        BackupsSettingsState.EnabledState.Active(
          expiresAt = activeSubscription.activeSubscription.endOfCurrentPeriod.seconds,
          lastBackupAt = SignalStore.backup.lastBackupTime.milliseconds,
          type = MessageBackupsType.Paid(
            pricePerMonth = FiatMoney.fromSignalNetworkAmount(
              activeSubscription.activeSubscription.amount,
              Currency.getInstance(activeSubscription.activeSubscription.currency)
            ),
            storageAllowanceBytes = backupType.storageAllowanceBytes
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
    return if (SignalStore.uiHints.hasEverEnabledRemoteBackups) {
      BackupsSettingsState.EnabledState.Inactive
    } else {
      BackupsSettingsState.EnabledState.Never
    }
  }
}
