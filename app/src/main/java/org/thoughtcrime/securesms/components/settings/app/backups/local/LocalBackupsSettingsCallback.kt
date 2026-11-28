/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.thoughtcrime.securesms.components.settings.app.backups.local

import android.Manifest
import android.content.ActivityNotFoundException
import android.net.Uri
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.FragmentActivity
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.appsettings.routes.navigateBack
import org.signal.appsettings.routes.navigateTo
import org.signal.core.ui.permissions.Permissions
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.jobs.LocalBackupJob.enqueueArchive
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.service.LocalBackupListener
import org.thoughtcrime.securesms.util.BackupUtil
import org.thoughtcrime.securesms.util.CommunicationActions

sealed interface LocalBackupsSettingsCallback {
  fun onNavigationClick()
  fun onTurnOnClick()
  fun onCreateBackupClick()
  fun onPickTimeClick()
  fun onViewBackupKeyClick()
  fun onLearnMoreClick()
  fun onLaunchBackupLocationPickerClick()
  fun onTurnOffAndDeleteConfirmed()
  fun onViewOptimizeStorageSettingClick()

  object Empty : LocalBackupsSettingsCallback {
    override fun onNavigationClick() = Unit
    override fun onTurnOnClick() = Unit
    override fun onCreateBackupClick() = Unit
    override fun onPickTimeClick() = Unit
    override fun onViewBackupKeyClick() = Unit
    override fun onLearnMoreClick() = Unit
    override fun onLaunchBackupLocationPickerClick() = Unit
    override fun onTurnOffAndDeleteConfirmed() = Unit
    override fun onViewOptimizeStorageSettingClick() = Unit
  }
}

class DefaultLocalBackupsSettingsCallback(
  private val activity: FragmentActivity,
  private val chooseBackupLocationLauncher: ActivityResultLauncher<Uri?>,
  private val viewModel: LocalBackupsViewModel,
  private val parentEventEmitter: (AppSettingsNavigationEvent) -> Unit
) : LocalBackupsSettingsCallback {

  companion object {
    private val TAG = Log.tag(LocalBackupsSettingsCallback::class)
  }

  override fun onNavigationClick() {
    parentEventEmitter.navigateBack()
  }

  override fun onLaunchBackupLocationPickerClick() {
    try {
      Log.d(TAG, "Starting choose backup location dialog")
      chooseBackupLocationLauncher.launch(SignalStore.settings.latestSignalBackupDirectory)
    } catch (_: ActivityNotFoundException) {
      Toast.makeText(activity, R.string.BackupDialog_no_file_picker_available, Toast.LENGTH_LONG).show()
    }
  }

  override fun onPickTimeClick() {
    val timeFormat = if (DateFormat.is24HourFormat(activity)) {
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
      SignalStore.settings.localBackupNextTime = 0
      LocalBackupListener.schedule(activity)
      viewModel.refreshSettingsState()
    }

    picker.show(activity.supportFragmentManager, "TIME_PICKER")
  }

  override fun onCreateBackupClick() {
    if (BackupUtil.isUserSelectionRequired(activity)) {
      Log.i(TAG, "Queueing backup...")
      viewModel.onBackupStarted()
      enqueueArchive(false)
    } else {
      Permissions.with(activity)
        .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        .ifNecessary()
        .onAllGranted {
          Log.i(TAG, "Queuing backup...")
          viewModel.onBackupStarted()
          enqueueArchive(false)
        }
        .withPermanentDenialDialog(
          activity.getString(R.string.BackupsPreferenceFragment_signal_requires_external_storage_permission_in_order_to_create_backups)
        )
        .execute()
    }
  }

  override fun onTurnOnClick() {
    if (BackupUtil.isUserSelectionRequired(activity)) {
      // When the user-selection flow is required, the screen shows a compose dialog and then
      // triggers [launchBackupDirectoryPicker] via callback.
      // This method intentionally does nothing in that case.
    } else {
      Permissions.with(activity)
        .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        .ifNecessary()
        .onAllGranted {
          onLaunchBackupLocationPickerClick()
        }
        .withPermanentDenialDialog(
          activity.getString(R.string.BackupsPreferenceFragment_signal_requires_external_storage_permission_in_order_to_create_backups)
        )
        .execute()
    }
  }

  override fun onViewBackupKeyClick() {
    if (SignalStore.account.isPhoneNumberless) {
      parentEventEmitter.navigateTo(AppSettingsRoute.AccountRoute.SignalLoginDetails)
    } else {
      parentEventEmitter.navigateTo(AppSettingsRoute.BackupsRoute.DisplayKey())
    }
  }

  override fun onLearnMoreClick() {
    CommunicationActions.openBrowserLink(activity, activity.getString(R.string.backup_support_url))
  }

  override fun onTurnOffAndDeleteConfirmed() {
    viewModel.turnOffAndDelete(activity)
  }

  override fun onViewOptimizeStorageSettingClick() {
    parentEventEmitter.navigateTo(AppSettingsRoute.DataAndStorageRoute.Storage)
  }
}
