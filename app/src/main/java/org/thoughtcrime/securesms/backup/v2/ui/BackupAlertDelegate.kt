/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui

import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.util.concurrent.SignalDispatchers
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.keyvalue.protos.BackupDownloadNotifierState

/**
 * Delegate that controls whether and which backup alert sheet is displayed.
 */
object BackupAlertDelegate {

  private const val FRAGMENT_TAG = "BackupAlertFragmentTag"

  @JvmStatic
  fun delegate(fragmentManager: FragmentManager, lifecycle: Lifecycle) {
    lifecycle.coroutineScope.launch {
      lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        if (BackupRepository.shouldDisplayBackupFailedSheet()) {
          BackupAlertBottomSheet.create(BackupAlert.BackupFailed).show(fragmentManager, FRAGMENT_TAG)
        } else if (BackupRepository.shouldDisplayCouldNotCompleteBackupSheet()) {
          BackupAlertBottomSheet.create(BackupAlert.CouldNotCompleteBackup(daysSinceLastBackup = SignalStore.backup.daysSinceLastBackup)).show(fragmentManager, FRAGMENT_TAG)
        } else if (BackupRepository.shouldDisplayBackupExpiredAndDowngradedSheet()) {
          BackupAlertBottomSheet.create(BackupAlert.ExpiredAndDowngraded).show(fragmentManager, FRAGMENT_TAG)
        } else if (BackupRepository.shouldDisplayNoManualBackupForTimeoutSheet()) {
          NoManualBackupBottomSheet().show(fragmentManager, FRAGMENT_TAG)
          BackupRepository.displayManualBackupNotCreatedInThresholdNotification()
        }

        displayBackupDownloadNotifier(fragmentManager)
      }
    }
  }

  private suspend fun displayBackupDownloadNotifier(fragmentManager: FragmentManager) {
    val downloadYourBackupToday = withContext(SignalDispatchers.IO) { BackupRepository.getDownloadYourBackupData() }
    when (downloadYourBackupToday?.type) {
      BackupDownloadNotifierState.Type.SHEET -> {
        BackupAlertBottomSheet.create(downloadYourBackupToday).show(fragmentManager, FRAGMENT_TAG)
      }
      BackupDownloadNotifierState.Type.DIALOG -> {
        DownloadYourBackupTodayDialog.create(downloadYourBackupToday).show(fragmentManager, FRAGMENT_TAG)
      }
      null -> Unit
    }
  }
}
