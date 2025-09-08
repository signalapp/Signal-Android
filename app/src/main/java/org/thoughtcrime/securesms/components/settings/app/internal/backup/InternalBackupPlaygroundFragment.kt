/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.internal.backup

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.SignalPreview
import org.signal.core.ui.compose.Snackbars
import org.signal.core.ui.compose.TextFields.TextField
import org.signal.core.util.Base64
import org.signal.core.util.Hex
import org.signal.core.util.getLength
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.backup.v2.ui.BackupAlert
import org.thoughtcrime.securesms.backup.v2.ui.BackupAlertBottomSheet
import org.thoughtcrime.securesms.components.settings.app.internal.backup.InternalBackupPlaygroundViewModel.DialogState
import org.thoughtcrime.securesms.components.settings.app.internal.backup.InternalBackupPlaygroundViewModel.ScreenState
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.ArchiveAttachmentBackfillJob
import org.thoughtcrime.securesms.jobs.ArchiveAttachmentReconciliationJob
import org.thoughtcrime.securesms.jobs.ArchiveThumbnailBackfillJob
import org.thoughtcrime.securesms.jobs.BackupRestoreMediaJob
import org.thoughtcrime.securesms.jobs.LocalBackupJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.Util

class InternalBackupPlaygroundFragment : ComposeFragment() {

  private val viewModel: InternalBackupPlaygroundViewModel by viewModels()
  private lateinit var saveEncryptedBackupToDiskLauncher: ActivityResultLauncher<Intent>
  private lateinit var savePlaintextBackupToDiskLauncher: ActivityResultLauncher<Intent>
  private lateinit var importEncryptedBackupFromDiskLauncher: ActivityResultLauncher<Intent>
  private lateinit var savePlaintextCopyLauncher: ActivityResultLauncher<Intent>

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    saveEncryptedBackupToDiskLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      if (result.resultCode == RESULT_OK) {
        result.data?.data?.let { uri ->
          viewModel.exportEncrypted(
            openStream = { requireContext().contentResolver.openOutputStream(uri)!! },
            appendStream = { requireContext().contentResolver.openOutputStream(uri, "wa")!! }
          )
        } ?: Toast.makeText(requireContext(), "No URI selected", Toast.LENGTH_SHORT).show()
      }
    }

    savePlaintextBackupToDiskLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      if (result.resultCode == RESULT_OK) {
        result.data?.data?.let { uri ->
          viewModel.exportPlaintext(
            openStream = { requireContext().contentResolver.openOutputStream(uri)!! },
            appendStream = { requireContext().contentResolver.openOutputStream(uri, "wa")!! }
          )
        } ?: Toast.makeText(requireContext(), "No URI selected", Toast.LENGTH_SHORT).show()
      }
    }

    importEncryptedBackupFromDiskLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      if (result.resultCode == RESULT_OK) {
        result.data?.data?.let { uri ->
          requireContext().contentResolver.getLength(uri)?.let { length ->
            viewModel.importEncryptedBackup(length) { requireContext().contentResolver.openInputStream(uri)!! }
          }
        } ?: Toast.makeText(requireContext(), "No URI selected", Toast.LENGTH_SHORT).show()
      }
    }

    savePlaintextCopyLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      if (result.resultCode == RESULT_OK) {
        result.data?.data?.let { uri ->
          viewModel.fetchRemoteBackupAndWritePlaintext(requireContext().contentResolver.openOutputStream(uri))
          Toast.makeText(requireContext(), "Check logs for progress.", Toast.LENGTH_SHORT).show()
        } ?: Toast.makeText(requireContext(), "No URI selected", Toast.LENGTH_SHORT).show()
      }
    }
  }

  @Composable
  override fun FragmentContent() {
    val context = LocalContext.current
    val state by viewModel.state
    val statsState by viewModel.statsState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
      viewModel.loadStats()
    }

    Tabs(
      onBack = { findNavController().popBackStack() },
      mainContent = {
        Screen(
          state = state,
          onCheckRemoteBackupStateClicked = { viewModel.checkRemoteBackupState() },
          onEnqueueRemoteBackupClicked = { viewModel.triggerBackupJob() },
          onEnqueueReconciliationClicked = { AppDependencies.jobManager.add(ArchiveAttachmentReconciliationJob(forced = true)) },
          onEnqueueAttachmentBackfillJob = { AppDependencies.jobManager.add(ArchiveAttachmentBackfillJob()) },
          onEnqueueThumbnailBackfillJob = { AppDependencies.jobManager.add(ArchiveThumbnailBackfillJob()) },
          onEnqueueMediaRestoreClicked = { AppDependencies.jobManager.add(BackupRestoreMediaJob()) },
          onHaltAllBackupJobsClicked = { viewModel.haltAllJobs() },
          onValidateBackupClicked = { viewModel.validateBackup() },
          onSaveEncryptedBackupToDiskClicked = {
            val intent = Intent().apply {
              action = Intent.ACTION_CREATE_DOCUMENT
              type = "application/octet-stream"
              addCategory(Intent.CATEGORY_OPENABLE)
              putExtra(Intent.EXTRA_TITLE, "backup-encrypted-${System.currentTimeMillis()}.bin")
            }

            saveEncryptedBackupToDiskLauncher.launch(intent)
          },
          onSavePlaintextBackupToDiskClicked = {
            val intent = Intent().apply {
              action = Intent.ACTION_CREATE_DOCUMENT
              type = "application/octet-stream"
              addCategory(Intent.CATEGORY_OPENABLE)
              putExtra(Intent.EXTRA_TITLE, "backup-plaintext-${System.currentTimeMillis()}.bin")
            }

            savePlaintextBackupToDiskLauncher.launch(intent)
          },
          onSavePlaintextCopyOfRemoteBackupClicked = {
            val intent = Intent().apply {
              action = Intent.ACTION_CREATE_DOCUMENT
              type = "application/octet-stream"
              addCategory(Intent.CATEGORY_OPENABLE)
              putExtra(Intent.EXTRA_TITLE, "backup-plaintext-${System.currentTimeMillis()}.binproto")
            }

            savePlaintextCopyLauncher.launch(intent)
          },
          onExportNewStyleLocalBackupClicked = { LocalBackupJob.enqueueArchive() },
          onWipeDataAndRestoreFromRemoteClicked = {
            MaterialAlertDialogBuilder(context)
              .setTitle("Are you sure?")
              .setMessage("This will delete all of your chats! Make sure you've finished a backup first, we don't check for you. Only do this on a test device!")
              .setPositiveButton("Wipe and restore") { _, _ ->
                Toast.makeText(this@InternalBackupPlaygroundFragment.requireContext(), "Restoring backup...", Toast.LENGTH_SHORT).show()
                viewModel.wipeAllDataAndRestoreFromRemote {
                  startActivity(MainActivity.clearTop(this@InternalBackupPlaygroundFragment.requireActivity()))
                }
              }
              .show()
          },
          onImportEncryptedBackupFromDiskClicked = {
            viewModel.onImportSelected()
          },
          onImportEncryptedBackupFromDiskDismissed = {
            viewModel.onDialogDismissed()
          },
          onImportEncryptedBackupFromDiskConfirmed = { aci, backupKey ->
            viewModel.onDialogDismissed()
            val valid = viewModel.onImportConfirmed(aci, backupKey)
            if (valid) {
              val intent = Intent().apply {
                action = Intent.ACTION_GET_CONTENT
                type = "application/octet-stream"
                addCategory(Intent.CATEGORY_OPENABLE)
              }
              importEncryptedBackupFromDiskLauncher.launch(intent)
            } else {
              Toast.makeText(context, "Invalid credentials!", Toast.LENGTH_SHORT).show()
            }
          },
          onImportNewStyleLocalBackupClicked = {
            MaterialAlertDialogBuilder(context)
              .setTitle("Are you sure?")
              .setMessage("After you choose a file to import, this will delete all of your chats, then restore them from the file! Only do this on a test device!")
              .setPositiveButton("Wipe and restore") { _, _ -> viewModel.import(SignalStore.settings.signalBackupDirectory!!) }
              .show()
          },
          onDeleteRemoteBackup = {
            MaterialAlertDialogBuilder(context)
              .setTitle("Are you sure?")
              .setMessage("This will delete all of your remote backup data!")
              .setPositiveButton("Delete remote data") { _, _ ->
                lifecycleScope.launch {
                  val success = viewModel.deleteRemoteBackupData()
                  withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), if (success) "Deleted!" else "Failed!", Toast.LENGTH_SHORT).show()
                  }
                }
              }
              .setNegativeButton("Cancel", null)
              .show()
          },
          onClearLocalMediaBackupState = {
            MaterialAlertDialogBuilder(context)
              .setTitle("Are you sure?")
              .setMessage("This will cause you to have to re-upload all of your media!")
              .setPositiveButton("Clear local media state") { _, _ ->
                lifecycleScope.launch {
                  viewModel.clearLocalMediaBackupState()
                  withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Done!", Toast.LENGTH_SHORT).show()
                  }
                }
              }
              .setNegativeButton("Cancel", null)
              .show()
          },
          onDisplayInitialBackupFailureSheet = {
            BackupRepository.displayInitialBackupFailureNotification()
            BackupAlertBottomSheet
              .create(BackupAlert.BackupFailed)
              .show(parentFragmentManager, null)
          }
        )
      },
      statsContent = {
        InternalBackupStatsTab(
          statsState,
          object : StatsCallbacks {
            override fun loadRemoteState() = viewModel.loadRemoteStats()
          }
        )
      }
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Tabs(
  onBack: () -> Unit,
  mainContent: @Composable () -> Unit,
  statsContent: @Composable () -> Unit
) {
  val tabs = listOf("Main", "Stats")
  var tabIndex by remember { mutableIntStateOf(0) }

  val snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }

  Scaffold(
    snackbarHost = { Snackbars.Host(snackbarHostState) },
    topBar = {
      Column {
        TopAppBar(
          title = {
            Text("Backup Playground")
          },
          navigationIcon = {
            IconButton(onClick = onBack) {
              Icon(
                painter = painterResource(R.drawable.symbol_arrow_start_24),
                tint = MaterialTheme.colorScheme.onSurface,
                contentDescription = null
              )
            }
          }
        )
        TabRow(selectedTabIndex = tabIndex) {
          tabs.forEachIndexed { index, tab ->
            Tab(
              text = { Text(tab) },
              selected = index == tabIndex,
              onClick = { tabIndex = index }
            )
          }
        }
      }
    }
  ) {
    Surface(modifier = Modifier.padding(it)) {
      when (tabIndex) {
        0 -> mainContent()
        1 -> statsContent()
      }
    }
  }
}

@Composable
fun Screen(
  state: ScreenState,
  onExportNewStyleLocalBackupClicked: () -> Unit = {},
  onImportNewStyleLocalBackupClicked: () -> Unit = {},
  onCheckRemoteBackupStateClicked: () -> Unit = {},
  onEnqueueRemoteBackupClicked: () -> Unit = {},
  onEnqueueReconciliationClicked: () -> Unit = {},
  onEnqueueMediaRestoreClicked: () -> Unit = {},
  onEnqueueAttachmentBackfillJob: () -> Unit = {},
  onEnqueueThumbnailBackfillJob: () -> Unit = {},
  onWipeDataAndRestoreFromRemoteClicked: () -> Unit = {},
  onHaltAllBackupJobsClicked: () -> Unit = {},
  onSavePlaintextCopyOfRemoteBackupClicked: () -> Unit = {},
  onValidateBackupClicked: () -> Unit = {},
  onSaveEncryptedBackupToDiskClicked: () -> Unit = {},
  onSavePlaintextBackupToDiskClicked: () -> Unit = {},
  onImportEncryptedBackupFromDiskClicked: () -> Unit = {},
  onImportEncryptedBackupFromDiskDismissed: () -> Unit = {},
  onImportEncryptedBackupFromDiskConfirmed: (aci: String, backupKey: String) -> Unit = { _, _ -> },
  onClearLocalMediaBackupState: () -> Unit = {},
  onDeleteRemoteBackup: () -> Unit = {},
  onDisplayInitialBackupFailureSheet: () -> Unit = {}
) {
  val context = LocalContext.current
  val scrollState = rememberScrollState()

  when (state.dialog) {
    DialogState.None -> Unit
    DialogState.ImportCredentials -> {
      ImportCredentialsDialog(
        onSubmit = onImportEncryptedBackupFromDiskConfirmed,
        onDismissed = onImportEncryptedBackupFromDiskDismissed
      )
    }
  }

  Surface {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(scrollState)
    ) {
      Rows.TextRow(
        text = {
          Text(
            text = state.statusMessage ?: "Status messages will appear here as you perform operations",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      )

      Dividers.Default()

      Rows.TextRow(
        text = "Check remote backup state",
        label = "Get a summary of what your remote backup looks like (presence, size, etc).",
        onClick = onCheckRemoteBackupStateClicked
      )

      Rows.TextRow(
        text = "Enqueue remote backup",
        label = "Schedules a job that will perform a routine remote backup.",
        onClick = onEnqueueRemoteBackupClicked
      )

      Rows.TextRow(
        text = "Enqueue reconciliation job",
        label = "Schedules a job that will ensure local and remote media state are in sync.",
        onClick = onEnqueueReconciliationClicked
      )

      Rows.TextRow(
        text = "Enqueue attachment backfill job",
        label = "Schedules a job that will upload any attachments that haven't been uploaded yet.",
        onClick = onEnqueueAttachmentBackfillJob
      )

      Rows.TextRow(
        text = "Enqueue thumbnail backfill job",
        label = "Schedules a job that will generate and upload any thumbnails that been uploaded yet.",
        onClick = onEnqueueThumbnailBackfillJob
      )

      Rows.TextRow(
        text = "Enqueue media restore job",
        label = "Schedules a job that will restore any NEEDS_RESTORE media.",
        onClick = onEnqueueMediaRestoreClicked
      )

      Rows.TextRow(
        text = "Halt all backup jobs",
        label = "Stops all backup-related jobs to the best of our ability.",
        onClick = onHaltAllBackupJobsClicked
      )

      Rows.TextRow(
        text = "Validate backup",
        label = "Generates a new backup and reports whether it passes validation. Does not save or upload anything.",
        onClick = onValidateBackupClicked
      )

      Rows.TextRow(
        text = "Save encrypted backup to disk",
        label = "Generates an encrypted backup (the same thing you would upload) and saves it to your local disk.",
        onClick = onSaveEncryptedBackupToDiskClicked
      )

      Rows.TextRow(
        text = "Save plaintext backup to disk",
        label = "Generates a plaintext, uncompressed backup and saves it to your local disk.",
        onClick = onSavePlaintextBackupToDiskClicked
      )

      Rows.TextRow(
        text = "Save plaintext copy of remote backup",
        label = "Downloads your most recently uploaded backup and saves it to disk, plaintext and uncompressed.",
        onClick = onSavePlaintextCopyOfRemoteBackupClicked
      )

      Rows.TextRow(
        text = "Perform a new-style local backup",
        label = "Creates a local backup (in your already-chosen backup directory) using the new on-disk backup format. This is the successor to the local backups existing users can build.",
        onClick = onExportNewStyleLocalBackupClicked
      )

      Rows.TextRow(
        text = "Copy Account Entropy Pool (AEP)",
        label = "Copies the Account Entropy Pool (AEP) to the clipboard, which is labeled as the \"Backup Key\" in the designs.",
        onClick = {
          Util.copyToClipboard(context, SignalStore.account.accountEntropyPool.value)
          Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
        }
      )

      Rows.TextRow(
        text = "Copy Cryptographic BackupKey",
        label = "Copies the cryptographic BackupKey to the clipboard as a hex string. Important: this is the key that is derived from the AEP, and therefore *not* the same as the key labeled \"Backup Key\" in the designs. That's actually the AEP, listed above.",
        onClick = {
          Util.copyToClipboard(context, Hex.toStringCondensed(SignalStore.account.accountEntropyPool.deriveMessageBackupKey().value))
          Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
        }
      )

      Rows.TextRow(
        text = "Copy Media Backup ID",
        label = "Copies the Media Backup ID, Base64 encoded; it can be used to identify your media backup on the server.",
        onClick = {
          Util.copyToClipboard(context, Base64.encodeWithoutPadding(SignalStore.backup.mediaRootBackupKey.deriveBackupId(SignalStore.account.requireAci()).value))
          Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
        }
      )

      Dividers.Default()

      Text(
        text = "DANGER ZONE",
        color = Color.Red,
        fontWeight = FontWeight.Bold
      )
      Rows.TextRow(
        text = {
          Text(
            text = "The following operations are potentially destructive! Only use them if you know what you're doing.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      )

      Rows.TextRow(
        text = "Wipe data and restore from remote",
        label = "Erases all content on your device, followed by a restore of your remote backup.",
        onClick = onWipeDataAndRestoreFromRemoteClicked
      )

      Rows.TextRow(
        text = "Clear backup init flag",
        label = "Clears our local state around whether backups have been initialized or not. Will force us to make request to claim backupId and set public keys.",
        onClick = {
          SignalStore.backup.backupsInitialized = false
        }
      )

      Rows.TextRow(
        text = "Clear backup credentials",
        label = "Clears any cached backup credentials, for both our message and media backups.",
        onClick = {
          SignalStore.backup.messageCredentials.clearAll()
          SignalStore.backup.mediaCredentials.clearAll()
        }
      )

      Rows.TextRow(
        text = "Wipe all data and restore from file",
        label = "Erases all content on your device, followed by a restore of an encrypted backup selected from disk.",
        onClick = onImportEncryptedBackupFromDiskClicked
      )

      Rows.TextRow(
        text = "Wipe all data and restore a new-style local backup",
        label = "Erases all content on your device, followed by a restore of a previously-generated new-style local backup.",
        onClick = onImportNewStyleLocalBackupClicked
      )

      Rows.TextRow(
        text = "Delete all backup data on server",
        label = "Erases all content on the server.",
        onClick = onDeleteRemoteBackup
      )

      Rows.TextRow(
        text = "Clear local media backup state",
        label = "Resets local state tracking so you think you haven't uploaded any media. The media still exists on the server.",
        onClick = onClearLocalMediaBackupState
      )

      Dividers.Default()

      Rows.TextRow(
        text = "Display initial backup failure sheet",
        label = "This will display the error sheet immediately and force the notification to display.",
        onClick = onDisplayInitialBackupFailureSheet
      )

      Rows.TextRow(
        text = "Mark backup failure",
        label = "This will display the error sheet when returning to the chats list.",
        onClick = {
          BackupRepository.markBackupFailure()
        }
      )

      Rows.TextRow(
        text = "Mark backup expired and downgraded",
        label = "This will not actually downgrade the user.",
        onClick = {
          SignalStore.backup.backupExpiredAndDowngraded = true
        }
      )

      Rows.TextRow(
        text = "Mark out of remote storage space",
        onClick = {
          BackupRepository.markOutOfRemoteStorageSpaceError()
        }
      )

      Spacer(modifier = Modifier.height(8.dp))
    }
  }
}

@Composable
private fun ImportCredentialsDialog(onSubmit: (aci: String, backupKey: String) -> Unit = { _, _ -> }, onDismissed: () -> Unit = {}) {
  val dialogScrollState = rememberScrollState()
  var aci by remember { mutableStateOf("") }
  var backupKey by remember { mutableStateOf("") }
  val inputOptions = KeyboardOptions(
    capitalization = KeyboardCapitalization.None,
    autoCorrectEnabled = false,
    keyboardType = KeyboardType.Ascii,
    imeAction = ImeAction.Next
  )
  androidx.compose.material3.AlertDialog(
    onDismissRequest = onDismissed,
    title = { Text(text = "Are you sure?") },
    text = {
      Column(modifier = Modifier.verticalScroll(dialogScrollState)) {
        Row(modifier = Modifier.padding(vertical = 10.dp)) {
          Text(text = "This will delete all of your chats! It's also not entirely realistic, because normally restores only happen during registration. Only do this on a test device!")
        }
        Row(modifier = Modifier.padding(vertical = 10.dp)) {
          TextField(
            value = aci,
            keyboardOptions = inputOptions,
            label = { Text("ACI") },
            supportingText = { Text("(leave blank for the current user)") },
            onValueChange = { aci = it }
          )
        }
        Row(modifier = Modifier.padding(vertical = 10.dp)) {
          TextField(
            value = backupKey,
            keyboardOptions = inputOptions.copy(imeAction = ImeAction.Done),
            label = { Text("Cryptographic BackupKey (*not* AEP!)") },
            supportingText = { Text("(leave blank for the current user)") },
            onValueChange = { backupKey = it }
          )
        }
      }
    },
    confirmButton = {
      TextButton(onClick = {
        onSubmit(aci, backupKey)
      }) {
        Text(text = "Wipe and restore")
      }
    },
    modifier = Modifier,
    properties = DialogProperties()
  )
}

@SignalPreview
@Composable
fun PreviewScreen() {
  Previews.Preview {
    Screen(state = ScreenState())
  }
}

@SignalPreview
@Composable
fun PreviewScreenExportInProgress() {
  Previews.Preview {
    Screen(state = ScreenState(statusMessage = "Some random status message."))
  }
}

@SignalPreview
@Composable
fun PreviewImportCredentialDialog() {
  Previews.Preview {
    ImportCredentialsDialog()
  }
}
