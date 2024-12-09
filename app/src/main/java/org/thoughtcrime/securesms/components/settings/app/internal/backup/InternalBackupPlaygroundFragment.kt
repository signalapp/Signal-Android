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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.signal.core.ui.Dividers
import org.signal.core.ui.Previews
import org.signal.core.ui.Rows
import org.signal.core.ui.SignalPreview
import org.signal.core.ui.Snackbars
import org.signal.core.util.getLength
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.attachments.AttachmentId
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.components.settings.app.internal.backup.InternalBackupPlaygroundViewModel.ScreenState
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.jobs.LocalBackupJob
import org.thoughtcrime.securesms.keyvalue.SignalStore

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
    val mediaState by viewModel.mediaState

    LaunchedEffect(Unit) {
      viewModel.loadMedia()
    }

    Tabs(
      onBack = { findNavController().popBackStack() },
      onDeleteAllArchivedMedia = { viewModel.deleteAllArchivedMedia() },
      mainContent = {
        Screen(
          state = state,
          onBackupTierSelected = { tier -> viewModel.onBackupTierSelected(tier) },
          onCheckRemoteBackupStateClicked = { viewModel.checkRemoteBackupState() },
          onEnqueueRemoteBackupClicked = { viewModel.triggerBackupJob() },
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
              .setPositiveButton("Wipe and restore") { _, _ -> viewModel.wipeAllDataAndRestoreFromRemote() }
              .show()
          },
          onImportEncryptedBackupFromDiskClicked = {
            val intent = Intent().apply {
              action = Intent.ACTION_GET_CONTENT
              type = "application/octet-stream"
              addCategory(Intent.CATEGORY_OPENABLE)
            }

            importEncryptedBackupFromDiskLauncher.launch(intent)
          },
          onImportNewStyleLocalBackupClicked = {
            MaterialAlertDialogBuilder(context)
              .setTitle("Are you sure?")
              .setMessage("After you choose a file to import, this will delete all of your chats, then restore them from the file! Only do this on a test device!")
              .setPositiveButton("Wipe and restore") { _, _ -> viewModel.import(SignalStore.settings.signalBackupDirectory!!) }
              .show()
          }
        )
      },
      mediaContent = { snackbarHostState ->
        MediaList(
          enabled = SignalStore.backup.backsUpMedia,
          state = mediaState,
          snackbarHostState = snackbarHostState,
          archiveAttachmentMedia = { viewModel.archiveAttachmentMedia(it) },
          deleteArchivedMedia = { viewModel.deleteArchivedMedia(it) },
          batchArchiveAttachmentMedia = { viewModel.archiveAttachmentMedia(it) },
          batchDeleteBackupAttachmentMedia = { viewModel.deleteArchivedMedia(it) },
          restoreArchivedMedia = { viewModel.restoreArchivedMedia(it, asThumbnail = false) },
          restoreArchivedMediaThumbnail = { viewModel.restoreArchivedMedia(it, asThumbnail = true) }
        )
      }
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Tabs(
  onBack: () -> Unit,
  onDeleteAllArchivedMedia: () -> Unit,
  mainContent: @Composable () -> Unit,
  mediaContent: @Composable (snackbarHostState: SnackbarHostState) -> Unit
) {
  val tabs = listOf("Main", "Media")
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
                painter = painterResource(R.drawable.symbol_arrow_left_24),
                tint = MaterialTheme.colorScheme.onSurface,
                contentDescription = null
              )
            }
          },
          actions = {
            if (tabIndex == 1 && SignalStore.backup.backsUpMedia) {
              TextButton(onClick = onDeleteAllArchivedMedia) {
                Text(text = "Delete All")
              }
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
        1 -> mediaContent(snackbarHostState)
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
  onWipeDataAndRestoreFromRemoteClicked: () -> Unit = {},
  onBackupTierSelected: (MessageBackupTier?) -> Unit = {},
  onHaltAllBackupJobsClicked: () -> Unit = {},
  onSavePlaintextCopyOfRemoteBackupClicked: () -> Unit = {},
  onValidateBackupClicked: () -> Unit = {},
  onSaveEncryptedBackupToDiskClicked: () -> Unit = {},
  onSavePlaintextBackupToDiskClicked: () -> Unit = {},
  onImportEncryptedBackupFromDiskClicked: () -> Unit = {}
) {
  val scrollState = rememberScrollState()
  val options = remember {
    mapOf(
      "None" to null,
      "Free" to MessageBackupTier.FREE,
      "Paid" to MessageBackupTier.PAID
    )
  }

  Surface {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(scrollState)
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Tier", fontWeight = FontWeight.Bold)
        options.forEach { option ->
          Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
              selected = option.value == state.backupTier,
              onClick = { onBackupTierSelected(option.value) }
            )
            Text(option.key)
          }
        }
      }

      Dividers.Default()

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

      Dividers.Default()

      Spacer(modifier = Modifier.height(8.dp))
    }
  }
}

@Composable
private fun StateLabel(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.labelSmall,
    textAlign = TextAlign.Center
  )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaList(
  enabled: Boolean,
  state: InternalBackupPlaygroundViewModel.MediaState,
  snackbarHostState: SnackbarHostState,
  archiveAttachmentMedia: (InternalBackupPlaygroundViewModel.BackupAttachment) -> Unit,
  deleteArchivedMedia: (InternalBackupPlaygroundViewModel.BackupAttachment) -> Unit,
  batchArchiveAttachmentMedia: (Set<AttachmentId>) -> Unit,
  batchDeleteBackupAttachmentMedia: (Set<AttachmentId>) -> Unit,
  restoreArchivedMedia: (InternalBackupPlaygroundViewModel.BackupAttachment) -> Unit,
  restoreArchivedMediaThumbnail: (InternalBackupPlaygroundViewModel.BackupAttachment) -> Unit
) {
  if (!enabled) {
    Text(
      text = "You do not have read/write to archive cdn enabled via SignalStore.backup",
      modifier = Modifier
        .padding(16.dp)
    )
    return
  }

  LaunchedEffect(state.error?.id) {
    state.error?.let {
      snackbarHostState.showSnackbar(it.errorText)
    }
  }

  var selectionState by remember { mutableStateOf(MediaMultiSelectState()) }

  Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
      items(
        count = state.attachments.size,
        key = { index -> state.attachments[index].id }
      ) { index ->
        val attachment = state.attachments[index]
        Row(
          modifier = Modifier
            .combinedClickable(
              onClick = {
                if (selectionState.selecting) {
                  selectionState = selectionState.copy(selected = if (selectionState.selected.contains(attachment.id)) selectionState.selected - attachment.id else selectionState.selected + attachment.id)
                }
              },
              onLongClick = {
                selectionState = if (selectionState.selecting) MediaMultiSelectState() else MediaMultiSelectState(selecting = true, selected = setOf(attachment.id))
              }
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
          if (selectionState.selecting) {
            Checkbox(
              checked = selectionState.selected.contains(attachment.id),
              onCheckedChange = { selected ->
                selectionState = selectionState.copy(selected = if (selected) selectionState.selected + attachment.id else selectionState.selected - attachment.id)
              }
            )
          }

          Column(modifier = Modifier.weight(1f, true)) {
            Text(text = attachment.title)
            Text(text = "State: ${attachment.state}")
          }

          if (attachment.state == InternalBackupPlaygroundViewModel.BackupAttachment.State.IN_PROGRESS) {
            CircularProgressIndicator()
          } else {
            Button(
              enabled = !selectionState.selecting,
              onClick = {
                when (attachment.state) {
                  InternalBackupPlaygroundViewModel.BackupAttachment.State.ATTACHMENT_CDN,
                  InternalBackupPlaygroundViewModel.BackupAttachment.State.LOCAL_ONLY -> archiveAttachmentMedia(attachment)

                  InternalBackupPlaygroundViewModel.BackupAttachment.State.UPLOADED_UNDOWNLOADED,
                  InternalBackupPlaygroundViewModel.BackupAttachment.State.UPLOADED_FINAL -> selectionState = selectionState.copy(expandedOption = attachment.dbAttachment.attachmentId)

                  else -> throw AssertionError("Unsupported state: ${attachment.state}")
                }
              }
            ) {
              Text(
                text = when (attachment.state) {
                  InternalBackupPlaygroundViewModel.BackupAttachment.State.ATTACHMENT_CDN,
                  InternalBackupPlaygroundViewModel.BackupAttachment.State.LOCAL_ONLY -> "Backup"

                  InternalBackupPlaygroundViewModel.BackupAttachment.State.UPLOADED_UNDOWNLOADED,
                  InternalBackupPlaygroundViewModel.BackupAttachment.State.UPLOADED_FINAL -> "Options..."

                  else -> throw AssertionError("Unsupported state: ${attachment.state}")
                }
              )

              DropdownMenu(
                expanded = attachment.dbAttachment.attachmentId == selectionState.expandedOption,
                onDismissRequest = { selectionState = selectionState.copy(expandedOption = null) }
              ) {
                DropdownMenuItem(
                  text = { Text("Remote Delete") },
                  onClick = {
                    selectionState = selectionState.copy(expandedOption = null)
                    deleteArchivedMedia(attachment)
                  }
                )

                DropdownMenuItem(
                  text = { Text("Pseudo Restore") },
                  onClick = {
                    selectionState = selectionState.copy(expandedOption = null)
                    restoreArchivedMedia(attachment)
                  }
                )

                DropdownMenuItem(
                  text = { Text("Pseudo Restore Thumbnail") },
                  onClick = {
                    selectionState = selectionState.copy(expandedOption = null)
                    restoreArchivedMediaThumbnail(attachment)
                  }
                )

                if (attachment.dbAttachment.dataHash != null && attachment.state == InternalBackupPlaygroundViewModel.BackupAttachment.State.UPLOADED_UNDOWNLOADED) {
                  DropdownMenuItem(
                    text = { Text("Re-copy with hash") },
                    onClick = {
                      selectionState = selectionState.copy(expandedOption = null)
                      archiveAttachmentMedia(attachment)
                    }
                  )
                }
              }
            }
          }
        }
      }
    }

    if (selectionState.selecting) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .padding(bottom = 24.dp)
          .background(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(8.dp)
          )
          .padding(8.dp)
      ) {
        Button(onClick = { selectionState = MediaMultiSelectState() }) {
          Text("Cancel")
        }
        Button(onClick = {
          batchArchiveAttachmentMedia(selectionState.selected)
          selectionState = MediaMultiSelectState()
        }) {
          Text("Backup")
        }
        Button(onClick = {
          batchDeleteBackupAttachmentMedia(selectionState.selected)
          selectionState = MediaMultiSelectState()
        }) {
          Text("Delete")
        }
      }
    }
  }
}

private data class MediaMultiSelectState(
  val selecting: Boolean = false,
  val selected: Set<AttachmentId> = emptySet(),
  val expandedOption: AttachmentId? = null
)

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
