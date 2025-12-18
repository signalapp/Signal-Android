/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.restore.local

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.util.Result
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.thoughtcrime.securesms.BaseActivity
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.backup.v2.RestoreV2Event
import org.thoughtcrime.securesms.backup.v2.local.ArchiveFileSystem
import org.thoughtcrime.securesms.backup.v2.local.LocalArchiver
import org.thoughtcrime.securesms.backup.v2.local.SnapshotFileSystem
import org.thoughtcrime.securesms.compose.SignalTheme
import org.thoughtcrime.securesms.conversation.v2.registerForLifecycle
import org.thoughtcrime.securesms.database.model.databaseprotos.RestoreDecisionState
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.RestoreLocalAttachmentJob
import org.thoughtcrime.securesms.keyvalue.Completed
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.registration.ui.restore.StorageServiceRestore
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationScreen
import org.thoughtcrime.securesms.registration.util.RegistrationUtil

/**
 * Internal only. On launch, attempt to import the most recent backup located in [SignalStore.backup].newLocalBackupsDirectory.
 */
class InternalNewLocalRestoreActivity : BaseActivity() {
  companion object {
    fun getIntent(context: Context, finish: Boolean = true): Intent = Intent(context, InternalNewLocalRestoreActivity::class.java).apply { putExtra("finish", finish) }
  }

  private var restoreStatus by mutableStateOf<String>("Unknown")

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    lifecycleScope.launch(Dispatchers.IO) {
      restoreStatus = "Starting..."

      val self = Recipient.self()
      val selfData = BackupRepository.SelfData(self.aci.get(), self.pni.get(), self.e164.get(), ProfileKey(self.profileKey))

      val archiveFileSystem = ArchiveFileSystem.fromUri(AppDependencies.application, Uri.parse(SignalStore.backup.newLocalBackupsDirectory!!))!!
      val snapshotInfo = archiveFileSystem.listSnapshots().first()
      val snapshotFileSystem = SnapshotFileSystem(AppDependencies.application, snapshotInfo.file)

      val result = LocalArchiver.import(snapshotFileSystem, selfData)

      if (result is Result.Success) {
        restoreStatus = "Success! Finishing"
        val mediaNameToFileInfo = archiveFileSystem.filesFileSystem.allFiles()
        RestoreLocalAttachmentJob.enqueueRestoreLocalAttachmentsJobs(mediaNameToFileInfo)

        SignalStore.registration.restoreDecisionState = RestoreDecisionState.Completed

        SignalStore.backup.backupSecretRestoreRequired = false
        StorageServiceRestore.restore()

        withContext(Dispatchers.Main) {
          Toast.makeText(this@InternalNewLocalRestoreActivity, "Local backup restored!", Toast.LENGTH_SHORT).show()
          RegistrationUtil.maybeMarkRegistrationComplete()
          startActivity(MainActivity.clearTop(this@InternalNewLocalRestoreActivity))
          if (intent.getBooleanExtra("finish", false)) {
            finishAffinity()
          }
        }
      } else {
        restoreStatus = "Backup failed"
        Toast.makeText(this@InternalNewLocalRestoreActivity, "Local backup failed", Toast.LENGTH_SHORT).show()
      }
    }

    setContent {
      SignalTheme {
        Surface {
          InternalNewLocalRestoreScreen(
            status = restoreStatus
          )
        }
      }
    }

    EventBus.getDefault().registerForLifecycle(subscriber = this, lifecycleOwner = this)
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun onEvent(restoreEvent: RestoreV2Event) {
    this.restoreStatus = "${restoreEvent.type}: ${restoreEvent.count} / ${restoreEvent.estimatedTotalCount}"
  }
}

@Composable
private fun InternalNewLocalRestoreScreen(
  status: String = ""
) {
  RegistrationScreen(
    title = "Internal - Local Restore",
    subtitle = null,
    bottomContent = { }
  ) {
    Text(
      text = status,
      modifier = Modifier
        .align(Alignment.CenterHorizontally)
        .padding(bottom = 16.dp)
    )

    CircularProgressIndicator(
      modifier = Modifier.align(Alignment.CenterHorizontally)
    )
  }
}

@DayNightPreviews
@Composable
private fun InternalNewLocalRestorePreview() {
  Previews.Preview {
    InternalNewLocalRestoreScreen(status = "Importing...")
  }
}
