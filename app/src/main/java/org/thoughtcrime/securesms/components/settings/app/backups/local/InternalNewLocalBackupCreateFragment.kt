/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.backups.local

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.map
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.v2.LocalBackupV2Event
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.conversation.v2.registerForLifecycle
import org.thoughtcrime.securesms.jobs.LocalBackupJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.service.LocalBackupListener
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.StorageUtil
import org.thoughtcrime.securesms.util.formatHours
import java.time.LocalTime
import java.util.Locale

/**
 * App settings internal screen for enabling and creating new local backups.
 */
class InternalNewLocalBackupCreateFragment : ComposeFragment() {

  private val TAG = Log.tag(InternalNewLocalBackupCreateFragment::class)

  private lateinit var chooseBackupLocationLauncher: ActivityResultLauncher<Intent>

  private var createStatus by mutableStateOf("None")
  private val directoryFlow = SignalStore.backup.newLocalBackupsDirectoryFlow.map { if (Build.VERSION.SDK_INT >= 24 && it != null) StorageUtil.getDisplayPath(requireContext(), Uri.parse(it)) else it }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    chooseBackupLocationLauncher = registerForActivityResult(
      ActivityResultContracts.StartActivityForResult()
    ) { result ->
      if (result.resultCode == Activity.RESULT_OK && result.data?.data != null) {
        handleBackupLocationSelected(result.data!!.data!!)
      } else {
        Log.w(TAG, "Backup location selection cancelled or failed")
      }
    }

    EventBus.getDefault().registerForLifecycle(subscriber = this, lifecycleOwner = this)
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun onEvent(event: LocalBackupV2Event) {
    createStatus = "${event.type}: ${event.count} / ${event.estimatedTotalCount}"
  }

  @Composable
  override fun FragmentContent() {
    val context = LocalContext.current
    val backupsEnabled by SignalStore.backup.newLocalBackupsEnabledFlow.collectAsStateWithLifecycle(SignalStore.backup.newLocalBackupsEnabled)
    val selectedDirectory by directoryFlow.collectAsStateWithLifecycle(SignalStore.backup.newLocalBackupsDirectory)
    val lastBackupTime by SignalStore.backup.newLocalBackupsLastBackupTimeFlow.collectAsStateWithLifecycle(SignalStore.backup.newLocalBackupsLastBackupTime)
    val lastBackupTimeString = remember(lastBackupTime) { calculateLastBackupTimeString(context, lastBackupTime) }
    val backupTime = remember { LocalTime.of(SignalStore.settings.backupHour, SignalStore.settings.backupMinute).formatHours(requireContext()) }

    InternalLocalBackupScreen(
      backupsEnabled = backupsEnabled,
      selectedDirectory = selectedDirectory,
      lastBackupTimeString = lastBackupTimeString,
      backupTime = backupTime,
      createStatus = createStatus,
      callback = CallbackImpl()
    )
  }

  private fun launchBackupDirectoryPicker() {
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)

    if (Build.VERSION.SDK_INT >= 26) {
      val latestDirectory = SignalStore.settings.latestSignalBackupDirectory
      if (latestDirectory != null) {
        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, latestDirectory)
      }
    }

    intent.addFlags(
      Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
        Intent.FLAG_GRANT_READ_URI_PERMISSION
    )

    try {
      Log.d(TAG, "Launching backup directory picker")
      chooseBackupLocationLauncher.launch(intent)
    } catch (e: Exception) {
      Log.w(TAG, "Failed to launch backup directory picker", e)
      Toast.makeText(requireContext(), R.string.BackupDialog_no_file_picker_available, Toast.LENGTH_LONG).show()
    }
  }

  private fun handleBackupLocationSelected(uri: Uri) {
    Log.i(TAG, "Backup location selected: $uri")

    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    requireContext().contentResolver.takePersistableUriPermission(uri, takeFlags)

    SignalStore.backup.newLocalBackupsDirectory = uri.toString()

    Toast.makeText(requireContext(), "Directory selected: $uri", Toast.LENGTH_SHORT).show()
  }

  private fun calculateLastBackupTimeString(context: Context, lastBackupTimestamp: Long): String {
    return if (lastBackupTimestamp > 0) {
      val relativeTime = DateUtils.getDatelessRelativeTimeSpanFormattedDate(
        context,
        Locale.getDefault(),
        lastBackupTimestamp
      )

      if (relativeTime.isRelative) {
        relativeTime.value
      } else {
        val day = DateUtils.getDayPrecisionTimeString(context, Locale.getDefault(), lastBackupTimestamp)
        val time = relativeTime.value

        context.getString(R.string.RemoteBackupsSettingsFragment__s_at_s, day, time)
      }
    } else {
      context.getString(R.string.RemoteBackupsSettingsFragment__never)
    }
  }

  private inner class CallbackImpl : Callback {
    override fun onNavigationClick() {
      requireActivity().onBackPressedDispatcher.onBackPressed()
    }

    override fun onToggleBackupsClick(enabled: Boolean) {
      SignalStore.backup.newLocalBackupsEnabled = enabled
      if (enabled) {
        LocalBackupListener.schedule(requireContext())
      }
    }

    override fun onSelectDirectoryClick() {
      launchBackupDirectoryPicker()
    }

    override fun onEnqueueBackupClick() {
      createStatus = "Starting..."
      LocalBackupJob.enqueueArchive(false)
    }
  }
}

private interface Callback {
  fun onNavigationClick()
  fun onToggleBackupsClick(enabled: Boolean)
  fun onSelectDirectoryClick()
  fun onEnqueueBackupClick()

  object Empty : Callback {
    override fun onNavigationClick() = Unit
    override fun onToggleBackupsClick(enabled: Boolean) = Unit
    override fun onSelectDirectoryClick() = Unit
    override fun onEnqueueBackupClick() = Unit
  }
}

@Composable
private fun InternalLocalBackupScreen(
  backupsEnabled: Boolean = false,
  selectedDirectory: String? = null,
  lastBackupTimeString: String = "Never",
  backupTime: String = "Unknown",
  createStatus: String = "None",
  callback: Callback
) {
  Scaffolds.Settings(
    title = "New Local Backups",
    navigationIcon = ImageVector.vectorResource(R.drawable.symbol_arrow_start_24),
    onNavigationClick = callback::onNavigationClick
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier.padding(paddingValues)
    ) {
      item {
        Rows.ToggleRow(
          checked = backupsEnabled,
          text = "Enable New Local Backups",
          label = if (backupsEnabled) "Backups are enabled" else "Backups are disabled",
          onCheckChanged = callback::onToggleBackupsClick
        )
      }

      item {
        Rows.TextRow(
          text = "Last Backup",
          label = lastBackupTimeString
        )
      }

      item {
        Rows.TextRow(
          text = "Backup Schedule Time (same as v1)",
          label = backupTime
        )
      }

      item {
        Rows.TextRow(
          text = "Select Backup Directory",
          label = selectedDirectory ?: "No directory selected",
          onClick = callback::onSelectDirectoryClick
        )
      }

      item {
        Rows.TextRow(
          text = "Create Backup Now",
          label = "Enqueue LocalArchiveJob",
          onClick = callback::onEnqueueBackupClick
        )
      }

      item {
        Rows.TextRow(
          text = "Create Status",
          label = createStatus
        )
      }
    }
  }
}

@DayNightPreviews
@Composable
fun InternalLocalBackupScreenPreview() {
  Previews.Preview {
    InternalLocalBackupScreen(
      backupsEnabled = true,
      selectedDirectory = "/storage/emulated/0/Signal/Backups",
      lastBackupTimeString = "1 hour ago",
      callback = Callback.Empty
    )
  }
}
