/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.thoughtcrime.securesms.components.settings.app.backups.local

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.DocumentsContract
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.navigation.fragment.findNavController
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.LocalBackupJob
import org.thoughtcrime.securesms.jobs.LocalBackupJob.enqueueArchive
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.service.LocalBackupListener
import org.thoughtcrime.securesms.util.BackupUtil
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.navigation.safeNavigate

sealed interface LocalBackupsSettingsCallback {
  fun onNavigationClick()
  fun onTurnOnClick()
  fun onCreateBackupClick()
  fun onPickTimeClick()
  fun onViewBackupKeyClick()
  fun onLearnMoreClick()
  fun onLaunchBackupLocationPickerClick()
  fun onTurnOffAndDeleteConfirmed()

  object Empty : LocalBackupsSettingsCallback {
    override fun onNavigationClick() = Unit
    override fun onTurnOnClick() = Unit
    override fun onCreateBackupClick() = Unit
    override fun onPickTimeClick() = Unit
    override fun onViewBackupKeyClick() = Unit
    override fun onLearnMoreClick() = Unit
    override fun onLaunchBackupLocationPickerClick() = Unit
    override fun onTurnOffAndDeleteConfirmed() = Unit
  }
}

class DefaultLocalBackupsSettingsCallback(
  private val fragment: LocalBackupsFragment,
  private val chooseBackupLocationLauncher: ActivityResultLauncher<Intent>,
  private val viewModel: LocalBackupsViewModel
) : LocalBackupsSettingsCallback {

  companion object {
    private val TAG = Log.tag(LocalBackupsSettingsCallback::class)
  }

  override fun onNavigationClick() {
    fragment.requireActivity().onBackPressedDispatcher.onBackPressed()
  }

  override fun onLaunchBackupLocationPickerClick() {
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)

    if (Build.VERSION.SDK_INT >= 26) {
      intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, SignalStore.settings.latestSignalBackupDirectory)
    }

    intent.addFlags(
      Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
        Intent.FLAG_GRANT_READ_URI_PERMISSION
    )

    try {
      Log.d(TAG, "Starting choose backup location dialog")
      chooseBackupLocationLauncher.launch(intent)
    } catch (e: ActivityNotFoundException) {
      Toast.makeText(fragment.requireContext(), R.string.BackupDialog_no_file_picker_available, Toast.LENGTH_LONG).show()
    }
  }

  override fun onPickTimeClick() {
    val timeFormat = if (DateFormat.is24HourFormat(fragment.requireContext())) {
      TimeFormat.CLOCK_24H
    } else {
      TimeFormat.CLOCK_12H
    }

    val picker = MaterialTimePicker.Builder()
      .setTimeFormat(timeFormat)
      .setHour(SignalStore.settings.backupHour)
      .setMinute(SignalStore.settings.backupMinute)
      .setTitleText(R.string.BackupsPreferenceFragment__set_backup_time)
      .build()

    picker.addOnPositiveButtonClickListener {
      SignalStore.settings.setBackupSchedule(picker.hour, picker.minute)
      TextSecurePreferences.setNextBackupTime(fragment.requireContext(), 0)
      LocalBackupListener.schedule(fragment.requireContext())
      viewModel.refreshSettingsState()
    }

    picker.show(fragment.childFragmentManager, "TIME_PICKER")
  }

  override fun onCreateBackupClick() {
    if (BackupUtil.isUserSelectionRequired(fragment.requireContext())) {
      Log.i(TAG, "Queueing backup...")
      enqueueArchive(false)
    } else {
      Permissions.with(fragment)
        .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        .ifNecessary()
        .onAllGranted {
          Log.i(TAG, "Queuing backup...")
          enqueueArchive(false)
        }
        .withPermanentDenialDialog(
          fragment.getString(R.string.BackupsPreferenceFragment_signal_requires_external_storage_permission_in_order_to_create_backups)
        )
        .execute()
    }
  }

  override fun onTurnOnClick() {
    if (BackupUtil.isUserSelectionRequired(fragment.requireContext())) {
      // When the user-selection flow is required, the screen shows a compose dialog and then
      // triggers [launchBackupDirectoryPicker] via callback.
      // This method intentionally does nothing in that case.
    } else {
      Permissions.with(fragment)
        .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        .ifNecessary()
        .onAllGranted {
          onLaunchBackupLocationPickerClick()
        }
        .withPermanentDenialDialog(
          fragment.getString(R.string.BackupsPreferenceFragment_signal_requires_external_storage_permission_in_order_to_create_backups)
        )
        .execute()
    }
  }

  override fun onViewBackupKeyClick() {
    fragment.findNavController().safeNavigate(R.id.action_backupsPreferenceFragment_to_backupKeyDisplayFragment)
  }

  override fun onLearnMoreClick() {
    CommunicationActions.openBrowserLink(fragment.requireContext(), fragment.getString(R.string.backup_support_url))
  }

  override fun onTurnOffAndDeleteConfirmed() {
    SignalStore.backup.newLocalBackupsEnabled = false

    val path = SignalStore.backup.newLocalBackupsDirectory
    SignalStore.backup.newLocalBackupsDirectory = null
    AppDependencies.jobManager.cancelAllInQueue(LocalBackupJob.QUEUE)
    BackupUtil.deleteUnifiedBackups(fragment.requireContext(), path)
  }
}
