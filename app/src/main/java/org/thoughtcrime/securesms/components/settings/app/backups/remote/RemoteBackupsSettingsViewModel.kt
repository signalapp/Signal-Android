/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.backups.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.backup.v2.BackupFrequency
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.components.settings.app.subscription.RecurringInAppPaymentRepository
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.BackupMessagesJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.service.MessageBackupListener
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * ViewModel for state management of RemoteBackupsSettingsFragment
 */
class RemoteBackupsSettingsViewModel : ViewModel() {

  companion object {
    private val TAG = Log.tag(RemoteBackupsSettingsFragment::class)
  }

  private val _state = MutableStateFlow(
    RemoteBackupsSettingsState(
      backupsInitialized = SignalStore.backup.backupsInitialized,
      messageBackupsType = null,
      lastBackupTimestamp = SignalStore.backup.lastBackupTime,
      backupSize = SignalStore.backup.totalBackupSize,
      backupsFrequency = SignalStore.backup.backupFrequency,
      canBackUpUsingCellular = SignalStore.backup.backupWithCellular
    )
  )

  val state: StateFlow<RemoteBackupsSettingsState> = _state

  fun setCanBackUpUsingCellular(canBackUpUsingCellular: Boolean) {
    SignalStore.backup.backupWithCellular = canBackUpUsingCellular
    _state.update { it.copy(canBackUpUsingCellular = canBackUpUsingCellular) }
  }

  fun setBackupsFrequency(backupsFrequency: BackupFrequency) {
    SignalStore.backup.backupFrequency = backupsFrequency
    _state.update { it.copy(backupsFrequency = backupsFrequency) }
    MessageBackupListener.setNextBackupTimeToIntervalFromNow()
    MessageBackupListener.schedule(AppDependencies.application)
  }

  fun requestDialog(dialog: RemoteBackupsSettingsState.Dialog) {
    _state.update { it.copy(dialog = dialog) }
  }

  fun requestSnackbar(snackbar: RemoteBackupsSettingsState.Snackbar) {
    _state.update { it.copy(snackbar = snackbar) }
  }

  fun refresh() {
    viewModelScope.launch {
      Log.d(TAG, "Attempting to synchronize backup tier from archive service.")

      val backupTier = withContext(Dispatchers.IO) {
        BackupRepository.getBackupTier()
      }

      backupTier.runIfSuccessful {
        Log.d(TAG, "Setting backup tier to $it")
        SignalStore.backup.backupTier = it
      }

      val tier = SignalStore.backup.backupTier
      val backupType = if (tier != null) BackupRepository.getBackupsType(tier) else null

      _state.update {
        it.copy(
          backupsInitialized = SignalStore.backup.backupsInitialized,
          messageBackupsType = backupType,
          backupState = RemoteBackupsSettingsState.BackupState.LOADING,
          lastBackupTimestamp = SignalStore.backup.lastBackupTime,
          backupSize = SignalStore.backup.totalBackupSize,
          backupsFrequency = SignalStore.backup.backupFrequency,
          canBackUpUsingCellular = SignalStore.backup.backupWithCellular
        )
      }

      when (tier) {
        MessageBackupTier.PAID -> {
          Log.d(TAG, "Attempting to retrieve subscription details for active PAID backup.")

          val activeSubscription = withContext(Dispatchers.IO) {
            RecurringInAppPaymentRepository.getActiveSubscriptionSync(InAppPaymentSubscriberRecord.Type.BACKUP)
          }

          if (activeSubscription.isSuccess) {
            Log.d(TAG, "Retrieved subscription details.")

            val subscription = activeSubscription.getOrThrow().activeSubscription
            if (subscription != null) {
              Log.d(TAG, "Subscription found. Updating UI state with subscription details.")
              _state.update {
                it.copy(
                  renewalTime = subscription.endOfCurrentPeriod.seconds,
                  backupState = when {
                    subscription.isActive -> RemoteBackupsSettingsState.BackupState.ACTIVE
                    subscription.isCanceled -> RemoteBackupsSettingsState.BackupState.CANCELED
                    else -> RemoteBackupsSettingsState.BackupState.INACTIVE
                  }
                )
              }
            } else {
              Log.d(TAG, "ActiveSubscription had null subscription object. Updating UI state with INACTIVE subscription.")
              _state.update {
                it.copy(
                  renewalTime = 0.seconds,
                  backupState = RemoteBackupsSettingsState.BackupState.INACTIVE
                )
              }
            }
          } else {
            Log.d(TAG, "Failed to load ActiveSubscription data. Updating UI state with error.")
            _state.update {
              it.copy(
                renewalTime = 0.seconds,
                backupState = RemoteBackupsSettingsState.BackupState.ERROR
              )
            }
          }
        }

        MessageBackupTier.FREE -> {
          Log.d(TAG, "Updating UI state with ACTIVE FREE tier.")
          _state.update { it.copy(renewalTime = 0.seconds, backupState = RemoteBackupsSettingsState.BackupState.ACTIVE) }
        }
        null -> {
          Log.d(TAG, "Updating UI state with INACTIVE null tier.")
          _state.update { it.copy(renewalTime = 0.seconds, backupState = RemoteBackupsSettingsState.BackupState.INACTIVE) }
        }
      }
    }
  }

  fun turnOffAndDeleteBackups() {
    viewModelScope.launch {
      requestDialog(RemoteBackupsSettingsState.Dialog.DELETING_BACKUP)

      val succeeded = withContext(Dispatchers.IO) {
        BackupRepository.turnOffAndDeleteBackup()
      }

      if (isActive) {
        if (succeeded) {
          requestDialog(RemoteBackupsSettingsState.Dialog.BACKUP_DELETED)
          delay(2000.milliseconds)
          requestDialog(RemoteBackupsSettingsState.Dialog.NONE)
          refresh()
        } else {
          requestDialog(RemoteBackupsSettingsState.Dialog.TURN_OFF_FAILED)
        }
      }
    }
  }

  fun onBackupNowClick() {
    BackupMessagesJob.enqueue()
  }
}
