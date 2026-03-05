/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.util.logging.Log
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.backup.v2.ImportResult
import org.thoughtcrime.securesms.backup.v2.RestoreV2Event
import org.thoughtcrime.securesms.backup.v2.local.ArchiveFileSystem
import org.thoughtcrime.securesms.conversation.v2.registerForLifecycle
import org.thoughtcrime.securesms.jobs.RestoreLocalAttachmentJob
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationScreen
import java.io.File
import java.io.FileInputStream

/**
 * Shows a spinner while importing a local backup for the quickstart build variant.
 *
 * Bypasses [org.thoughtcrime.securesms.backup.v2.local.LocalArchiver] and
 * [org.thoughtcrime.securesms.backup.v2.local.SnapshotFileSystem] to avoid
 * DocumentFile findFile issues. Instead, finds backup files using raw [File] I/O
 * and calls [BackupRepository.importLocal] directly.
 *
 * Requires MANAGE_EXTERNAL_STORAGE on Android 11+ to read from /sdcard/.
 * Will prompt the user to grant the permission if not already granted.
 */
class QuickstartRestoreActivity : BaseActivity() {

  companion object {
    private val TAG = Log.tag(QuickstartRestoreActivity::class.java)

    /**
     * Finds a file in [dir] by trying the base name first, then common SAF-appended
     * extensions (.bin for application/octet-stream).
     */
    private fun findBackupFile(dir: File, baseName: String): File? {
      val candidates = listOf(baseName, "$baseName.bin")
      return candidates
        .map { File(dir, it) }
        .firstOrNull { it.exists() }
    }
  }

  private var restoreStatus by mutableStateOf("Restoring data...")

  private val manageStorageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
    if (hasStorageAccess()) {
      startRestore()
    } else {
      Log.w(TAG, "MANAGE_EXTERNAL_STORAGE not granted after returning from Settings")
      restoreStatus = "Storage permission required. Please grant 'All files access' and relaunch."
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContent {
      SignalTheme {
        Surface {
          RegistrationScreen(
            title = "Quickstart Restore",
            subtitle = null,
            bottomContent = { }
          ) {
            Text(
              text = restoreStatus,
              modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 16.dp)
            )

            CircularProgressIndicator(
              modifier = Modifier.align(Alignment.CenterHorizontally)
            )
          }
        }
      }
    }

    org.greenrobot.eventbus.EventBus.getDefault().registerForLifecycle(subscriber = this, lifecycleOwner = this)

    if (hasStorageAccess()) {
      startRestore()
    } else {
      Log.i(TAG, "MANAGE_EXTERNAL_STORAGE not granted, requesting...")
      restoreStatus = "Requesting storage permission..."
      val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
      manageStorageLauncher.launch(intent)
    }
  }

  private fun hasStorageAccess(): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
  }

  private fun startRestore() {
    lifecycleScope.launch(Dispatchers.IO) {
      try {
        val self = Recipient.self()
        val selfData = BackupRepository.SelfData(self.aci.get(), self.pni.get(), self.e164.get(), ProfileKey(self.profileKey))

        val backupDir = QuickstartInitializer.pendingBackupDir!!

        // Find snapshot directory using raw File API
        val signalBackupsDir = File(backupDir, "SignalBackups")
        val snapshotDir = signalBackupsDir.listFiles()
          ?.filter { it.isDirectory && it.name.startsWith("signal-backup") }
          ?.sortedByDescending { it.name }
          ?.firstOrNull()
          ?: error("No snapshot directory found in ${signalBackupsDir.absolutePath}")

        Log.i(TAG, "Snapshot directory: ${snapshotDir.absolutePath}")
        Log.i(TAG, "Snapshot contents: ${snapshotDir.listFiles()?.joinToString { "${it.name} (${if (it.isDirectory) "dir" else "${it.length()}b"})" }}")

        // Find the main backup file (may be "main" or "main.bin" depending on how the backup was created)
        val mainFile = findBackupFile(snapshotDir, "main")
          ?: error("No 'main' file found in snapshot directory ${snapshotDir.absolutePath}")

        Log.i(TAG, "Using main file: ${mainFile.name} (${mainFile.length()} bytes)")

        // Import directly via BackupRepository, bypassing SnapshotFileSystem/LocalArchiver
        // to avoid DocumentFile.findFile name-matching issues
        val importResult = BackupRepository.importLocal(
          mainStreamFactory = { FileInputStream(mainFile) },
          mainStreamLength = mainFile.length(),
          selfData = selfData
        )

        Log.i(TAG, "Import result: $importResult")

        if (importResult is ImportResult.Failure) {
          error("BackupRepository.importLocal returned Failure")
        }

        withContext(Dispatchers.Main) { restoreStatus = "Restoring attachments..." }

        // Enqueue attachment restore jobs via ArchiveFileSystem (which handles the files/ directory)
        val archiveFileSystem = ArchiveFileSystem.fromFile(applicationContext, backupDir)
        val mediaNameToFileInfo = archiveFileSystem.filesFileSystem.allFiles()
        RestoreLocalAttachmentJob.enqueueRestoreLocalAttachmentsJobs(mediaNameToFileInfo)

        QuickstartInitializer.pendingBackupDir = null

        withContext(Dispatchers.Main) {
          Toast.makeText(this@QuickstartRestoreActivity, "Backup restored!", Toast.LENGTH_SHORT).show()
          startActivity(MainActivity.clearTop(this@QuickstartRestoreActivity))
          finishAffinity()
        }
      } catch (e: Exception) {
        Log.w(TAG, "Error during quickstart restore", e)
        QuickstartInitializer.pendingBackupDir = null

        withContext(Dispatchers.Main) {
          Toast.makeText(this@QuickstartRestoreActivity, "Backup restore failed: ${e.message}", Toast.LENGTH_LONG).show()
          startActivity(MainActivity.clearTop(this@QuickstartRestoreActivity))
          finishAffinity()
        }
      }
    }
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun onEvent(restoreEvent: RestoreV2Event) {
    restoreStatus = "${restoreEvent.type}: ${restoreEvent.count} / ${restoreEvent.estimatedTotalCount}"
  }
}
