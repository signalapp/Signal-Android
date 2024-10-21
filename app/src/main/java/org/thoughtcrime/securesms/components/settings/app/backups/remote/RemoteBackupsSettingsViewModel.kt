/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.backups.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.withContext
import org.signal.core.util.logging.Log
import org.signal.core.util.money.FiatMoney
import org.signal.donations.InAppPaymentType
import org.thoughtcrime.securesms.backup.v2.BackupFrequency
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.backup.v2.ui.status.BackupStatusData
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsType
import org.thoughtcrime.securesms.banner.banners.MediaRestoreProgressBanner
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationSerializationHelper.toFiatMoney
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.RecurringInAppPaymentRepository
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.BackupMessagesJob
import org.thoughtcrime.securesms.jobs.RestoreOptimizedMediaJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.service.MessageBackupListener
import java.util.Currency
import kotlin.time.Duration.Companion.seconds

/**
 * ViewModel for state management of RemoteBackupsSettingsFragment
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RemoteBackupsSettingsViewModel : ViewModel() {

  companion object {
    private val TAG = Log.tag(RemoteBackupsSettingsFragment::class)
  }

  private val _state = MutableStateFlow(
    RemoteBackupsSettingsState(
      backupsEnabled = SignalStore.backup.areBackupsEnabled,
      lastBackupTimestamp = SignalStore.backup.lastBackupTime,
      backupSize = SignalStore.backup.totalBackupSize,
      backupsFrequency = SignalStore.backup.backupFrequency,
      canBackUpUsingCellular = SignalStore.backup.backupWithCellular
    )
  )

  private val _restoreState: MutableStateFlow<BackupRestoreState> = MutableStateFlow(BackupRestoreState(false, BackupStatusData.RestoringMedia()))
  private val latestPurchaseId = MutableSharedFlow<InAppPaymentTable.InAppPaymentId>()

  val state: StateFlow<RemoteBackupsSettingsState> = _state
  val restoreState: StateFlow<BackupRestoreState> = _restoreState

  init {
    viewModelScope.launch(Dispatchers.IO) {
      latestPurchaseId
        .flatMapLatest { id -> InAppPaymentsRepository.observeUpdates(id).asFlow() }
        .collectLatest { purchase ->
          refreshState(purchase)
        }
    }

    viewModelScope.launch(Dispatchers.Default) {
      val restoreProgress = MediaRestoreProgressBanner()

      while (isActive) {
        if (restoreProgress.enabled) {
          Log.d(TAG, "Backup is being restored. Collecting updates.")
          restoreProgress.dataFlow.collectLatest { latest ->
            _restoreState.update { BackupRestoreState(restoreProgress.enabled, latest) }
          }
        }

        delay(1.seconds)
      }
    }
  }

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
    viewModelScope.launch(Dispatchers.IO) {
      val id = SignalDatabase.inAppPayments.getLatestInAppPaymentByType(InAppPaymentType.RECURRING_BACKUP)?.id

      if (id != null) {
        latestPurchaseId.emit(id)
      } else {
        refreshState(null)
      }
    }
  }

  private suspend fun refreshState(lastPurchase: InAppPaymentTable.InAppPayment?) {
    val tier = SignalStore.backup.latestBackupTier

    _state.update {
      it.copy(
        backupsEnabled = SignalStore.backup.areBackupsEnabled,
        backupState = RemoteBackupsSettingsState.BackupState.Loading,
        lastBackupTimestamp = SignalStore.backup.lastBackupTime,
        backupSize = SignalStore.backup.totalBackupSize,
        backupsFrequency = SignalStore.backup.backupFrequency,
        canBackUpUsingCellular = SignalStore.backup.backupWithCellular
      )
    }

    if (lastPurchase?.state == InAppPaymentTable.State.PENDING) {
      Log.d(TAG, "We have a pending subscription.")
      _state.update {
        it.copy(
          backupState = RemoteBackupsSettingsState.BackupState.Pending(
            price = lastPurchase.data.amount!!.toFiatMoney()
          )
        )
      }

      return
    }

    when (tier) {
      MessageBackupTier.PAID -> {
        Log.d(TAG, "Attempting to retrieve subscription details for active PAID backup.")

        val type = withContext(Dispatchers.IO) {
          BackupRepository.getBackupsType(tier) as MessageBackupsType.Paid
        }

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
                backupState = when {
                  subscription.isActive -> RemoteBackupsSettingsState.BackupState.ActivePaid(
                    messageBackupsType = type,
                    price = FiatMoney.fromSignalNetworkAmount(subscription.amount, Currency.getInstance(subscription.currency)),
                    renewalTime = subscription.endOfCurrentPeriod.seconds
                  )
                  subscription.isCanceled -> RemoteBackupsSettingsState.BackupState.Canceled(
                    messageBackupsType = type,
                    renewalTime = subscription.endOfCurrentPeriod.seconds
                  )
                  else -> RemoteBackupsSettingsState.BackupState.Inactive(
                    messageBackupsType = type,
                    renewalTime = subscription.endOfCurrentPeriod.seconds
                  )
                }
              )
            }
          } else {
            Log.d(TAG, "ActiveSubscription had null subscription object. Updating UI state with INACTIVE subscription.")
            _state.update {
              it.copy(
                backupState = RemoteBackupsSettingsState.BackupState.Inactive(type)
              )
            }
          }
        } else {
          Log.d(TAG, "Failed to load ActiveSubscription data. Updating UI state with error.")
          _state.update {
            it.copy(
              backupState = RemoteBackupsSettingsState.BackupState.Error
            )
          }
        }
      }

      MessageBackupTier.FREE -> {
        val type = withContext(Dispatchers.IO) {
          BackupRepository.getBackupsType(tier) as MessageBackupsType.Free
        }

        val backupState = if (SignalStore.backup.areBackupsEnabled) {
          RemoteBackupsSettingsState.BackupState.ActiveFree(type)
        } else {
          RemoteBackupsSettingsState.BackupState.Inactive(type)
        }

        Log.d(TAG, "Updating UI state with $backupState FREE tier.")
        _state.update { it.copy(backupState = backupState) }
      }

      null -> {
        Log.d(TAG, "Updating UI state with NONE null tier.")
        _state.update { it.copy(backupState = RemoteBackupsSettingsState.BackupState.None) }
      }
    }
  }

  fun turnOffAndDeleteBackups() {
    viewModelScope.launch {
      Log.d(TAG, "Beginning to turn off and delete backup.")
      requestDialog(RemoteBackupsSettingsState.Dialog.PROGRESS_SPINNER)

      val hasMediaBackupUploaded = SignalStore.backup.backsUpMedia && SignalStore.backup.hasBackupBeenUploaded

      val succeeded = withContext(Dispatchers.IO) {
        BackupRepository.turnOffAndDisableBackups()
      }

      if (isActive) {
        if (succeeded) {
          if (hasMediaBackupUploaded && SignalStore.backup.optimizeStorage) {
            Log.d(TAG, "User has optimized storage, downloading.")
            requestDialog(RemoteBackupsSettingsState.Dialog.DOWNLOADING_YOUR_BACKUP)

            SignalStore.backup.optimizeStorage = false
            RestoreOptimizedMediaJob.enqueue()
          } else {
            Log.d(TAG, "User does not have optimized storage, finished.")
            requestDialog(RemoteBackupsSettingsState.Dialog.NONE)
          }
          refresh()
        } else {
          Log.d(TAG, "Failed to disable backups.")
          requestDialog(RemoteBackupsSettingsState.Dialog.TURN_OFF_FAILED)
        }
      }
    }
  }

  fun onBackupNowClick() {
    BackupMessagesJob.enqueue()
  }
}
