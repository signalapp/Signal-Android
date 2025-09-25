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
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.keyvalue.protos.BackupDownloadNotifierState

/**
 * Delegate that controls whether and which backup alert sheet is displayed.
 */
object BackupAlertDelegate {

  private const val FRAGMENT_TAG = "BackupAlertFragmentTag"
  private val TAG = Log.tag(BackupAlertDelegate::class)

  @JvmStatic
  fun delegate(fragmentManager: FragmentManager, lifecycle: Lifecycle) {
    lifecycle.coroutineScope.launch {
      lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        if (BackupRepository.shouldDisplayBackupFailedSheet()) {
          Log.d(TAG, "Displaying BackupFailed sheet.")
          BackupAlertBottomSheet.create(BackupAlert.BackupFailed).show(fragmentManager, FRAGMENT_TAG)
        } else if (BackupRepository.shouldDisplayCouldNotCompleteBackupSheet()) {
          Log.d(TAG, "Displaying CouldNotCompleteBackup sheet.")
          BackupAlertBottomSheet.create(BackupAlert.CouldNotCompleteBackup(daysSinceLastBackup = SignalStore.backup.daysSinceLastBackup)).show(fragmentManager, FRAGMENT_TAG)
        } else if (BackupRepository.shouldDisplayBackupExpiredAndDowngradedSheet()) {
          Log.d(TAG, "Displaying ExpiredAndDowngraded sheet.")
          BackupAlertBottomSheet.create(BackupAlert.ExpiredAndDowngraded).show(fragmentManager, FRAGMENT_TAG)
        } else if (BackupRepository.shouldDisplayOutOfRemoteStorageSpaceSheet()) {
          Log.d(TAG, "Displaying NoRemoteStorageSpaceAvailableBottomSheet.")
          NoRemoteStorageSpaceAvailableBottomSheet().show(fragmentManager, FRAGMENT_TAG)
        }

        displayBackupDownloadNotifier(fragmentManager)
      }
    }
  }

  private suspend fun displayBackupDownloadNotifier(fragmentManager: FragmentManager) {
    val downloadYourBackupToday = withContext(SignalDispatchers.IO) { BackupRepository.getDownloadYourBackupData() }
    when (downloadYourBackupToday?.type) {
      BackupDownloadNotifierState.Type.SHEET -> {
        Log.d(TAG, "Displaying 'Download your backup today' sheet.")
        BackupAlertBottomSheet.create(downloadYourBackupToday).show(fragmentManager, FRAGMENT_TAG)
      }
      BackupDownloadNotifierState.Type.DIALOG -> {
        Log.d(TAG, "Displaying 'Download your backup today' dialog.")
        DownloadYourBackupTodayDialog.create(downloadYourBackupToday).show(fragmentManager, FRAGMENT_TAG)
      }
      null -> Unit
    }
  }
}
