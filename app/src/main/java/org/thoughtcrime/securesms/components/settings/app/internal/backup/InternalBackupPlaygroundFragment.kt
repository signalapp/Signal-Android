/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.internal.backup

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.content.res.Configuration
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import org.signal.core.ui.Buttons
import org.signal.core.ui.Dividers
import org.signal.core.ui.Snackbars
import org.signal.core.ui.theme.SignalTheme
import org.signal.core.util.bytes
import org.signal.core.util.getLength
import org.signal.core.util.roundedString
import org.thoughtcrime.securesms.components.settings.app.internal.backup.InternalBackupPlaygroundViewModel.BackupState
import org.thoughtcrime.securesms.components.settings.app.internal.backup.InternalBackupPlaygroundViewModel.BackupUploadState
import org.thoughtcrime.securesms.components.settings.app.internal.backup.InternalBackupPlaygroundViewModel.ScreenState
import org.thoughtcrime.securesms.compose.ComposeFragment

class InternalBackupPlaygroundFragment : ComposeFragment() {

  private val viewModel: InternalBackupPlaygroundViewModel by viewModels()
  private lateinit var exportFileLauncher: ActivityResultLauncher<Intent>
  private lateinit var importFileLauncher: ActivityResultLauncher<Intent>
  private lateinit var validateFileLauncher: ActivityResultLauncher<Intent>

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    exportFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      if (result.resultCode == RESULT_OK) {
        result.data?.data?.let { uri ->
          requireContext().contentResolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.write(viewModel.backupData!!)
            Toast.makeText(requireContext(), "Saved successfully", Toast.LENGTH_SHORT).show()
          } ?: Toast.makeText(requireContext(), "Failed to open output stream", Toast.LENGTH_SHORT).show()
        } ?: Toast.makeText(requireContext(), "No URI selected", Toast.LENGTH_SHORT).show()
      }
    }

    importFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      if (result.resultCode == RESULT_OK) {
        result.data?.data?.let { uri ->
          requireContext().contentResolver.getLength(uri)?.let { length ->
            viewModel.import(length) { requireContext().contentResolver.openInputStream(uri)!! }
          }
        } ?: Toast.makeText(requireContext(), "No URI selected", Toast.LENGTH_SHORT).show()
      }
    }

    validateFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      if (result.resultCode == RESULT_OK) {
        result.data?.data?.let { uri ->
          requireContext().contentResolver.getLength(uri)?.let { length ->
            viewModel.validate(length) { requireContext().contentResolver.openInputStream(uri)!! }
          }
        } ?: Toast.makeText(requireContext(), "No URI selected", Toast.LENGTH_SHORT).show()
      }
    }
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state
    val mediaState by viewModel.mediaState

    LaunchedEffect(Unit) {
      viewModel.loadMedia()
    }

    Tabs(
      mainContent = {
        Screen(
          state = state,
          onExportClicked = { viewModel.export() },
          onImportMemoryClicked = { viewModel.import() },
          onImportFileClicked = {
            val intent = Intent().apply {
              action = Intent.ACTION_GET_CONTENT
              type = "application/octet-stream"
              addCategory(Intent.CATEGORY_OPENABLE)
            }

            importFileLauncher.launch(intent)
          },
          onPlaintextClicked = { viewModel.onPlaintextToggled() },
          onSaveToDiskClicked = {
            val intent = Intent().apply {
              action = Intent.ACTION_CREATE_DOCUMENT
              type = "application/octet-stream"
              addCategory(Intent.CATEGORY_OPENABLE)
              putExtra(Intent.EXTRA_TITLE, "backup-${if (state.plaintext) "plaintext" else "encrypted"}-${System.currentTimeMillis()}.bin")
            }

            exportFileLauncher.launch(intent)
          },
          onUploadToRemoteClicked = { viewModel.uploadBackupToRemote() },
          onCheckRemoteBackupStateClicked = { viewModel.checkRemoteBackupState() },
          onValidateFileClicked = {
            val intent = Intent().apply {
              action = Intent.ACTION_GET_CONTENT
              type = "application/octet-stream"
              addCategory(Intent.CATEGORY_OPENABLE)
            }

            validateFileLauncher.launch(intent)
          }
        )
      },
      mediaContent = { snackbarHostState ->
        MediaList(
          state = mediaState,
          snackbarHostState = snackbarHostState,
          backupAttachmentMedia = { viewModel.backupAttachmentMedia(it) },
          deleteBackupAttachmentMedia = { viewModel.deleteBackupAttachmentMedia(it) },
          batchBackupAttachmentMedia = { viewModel.backupAttachmentMedia(it) },
          batchDeleteBackupAttachmentMedia = { viewModel.deleteBackupAttachmentMedia(it) }
        )
      }
    )
  }
}

@Composable
fun Tabs(
  mainContent: @Composable () -> Unit,
  mediaContent: @Composable (snackbarHostState: SnackbarHostState) -> Unit
) {
  val tabs = listOf("Main", "Media")
  var tabIndex by remember { mutableIntStateOf(0) }

  val snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }

  Scaffold(
    snackbarHost = { Snackbars.Host(snackbarHostState) },
    topBar = {
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
  onExportClicked: () -> Unit = {},
  onImportMemoryClicked: () -> Unit = {},
  onImportFileClicked: () -> Unit = {},
  onPlaintextClicked: () -> Unit = {},
  onSaveToDiskClicked: () -> Unit = {},
  onValidateFileClicked: () -> Unit = {},
  onUploadToRemoteClicked: () -> Unit = {},
  onCheckRemoteBackupStateClicked: () -> Unit = {}
) {
  Surface {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
      modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically
      ) {
        StateLabel(text = "Plaintext?")
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
          checked = state.plaintext,
          onCheckedChange = { onPlaintextClicked() }
        )
      }

      Spacer(modifier = Modifier.height(8.dp))

      Buttons.LargePrimary(
        onClick = onExportClicked,
        enabled = !state.backupState.inProgress
      ) {
        Text("Export")
      }

      Dividers.Default()

      Buttons.LargeTonal(
        onClick = onImportMemoryClicked,
        enabled = state.backupState == BackupState.EXPORT_DONE
      ) {
        Text("Import from memory")
      }
      Buttons.LargeTonal(
        onClick = onImportFileClicked
      ) {
        Text("Import from file")
      }

      Buttons.LargeTonal(
        onClick = onValidateFileClicked
      ) {
        Text("Validate file")
      }

      Spacer(modifier = Modifier.height(16.dp))

      when (state.backupState) {
        BackupState.NONE -> {
          StateLabel("")
        }

        BackupState.EXPORT_IN_PROGRESS -> {
          StateLabel("Export in progress...")
        }

        BackupState.EXPORT_DONE -> {
          StateLabel("Export complete. Sitting in memory. You can click 'Import' to import that data, save it to a file, or upload it to remote.")

          Spacer(modifier = Modifier.height(8.dp))

          Buttons.MediumTonal(onClick = onSaveToDiskClicked) {
            Text("Save to file")
          }
        }

        BackupState.IMPORT_IN_PROGRESS -> {
          StateLabel("Import in progress...")
        }
      }

      Dividers.Default()

      Buttons.LargeTonal(
        onClick = onCheckRemoteBackupStateClicked
      ) {
        Text("Check remote backup state")
      }

      Spacer(modifier = Modifier.height(8.dp))

      when (state.remoteBackupState) {
        is InternalBackupPlaygroundViewModel.RemoteBackupState.Available -> {
          StateLabel("Exists/allocated. ${state.remoteBackupState.response.mediaCount} media items, using ${state.remoteBackupState.response.usedSpace} bytes (${state.remoteBackupState.response.usedSpace.bytes.inMebiBytes.roundedString(3)} MiB)")
        }

        InternalBackupPlaygroundViewModel.RemoteBackupState.GeneralError -> {
          StateLabel("Hit an unknown error. Check the logs.")
        }

        InternalBackupPlaygroundViewModel.RemoteBackupState.NotFound -> {
          StateLabel("Not found.")
        }

        InternalBackupPlaygroundViewModel.RemoteBackupState.Unknown -> {
          StateLabel("Hit the button above to check the state.")
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      Buttons.LargePrimary(
        onClick = onUploadToRemoteClicked,
        enabled = state.backupState == BackupState.EXPORT_DONE
      ) {
        Text("Upload to remote")
      }

      Spacer(modifier = Modifier.height(8.dp))

      when (state.uploadState) {
        BackupUploadState.NONE -> {
          StateLabel("")
        }

        BackupUploadState.UPLOAD_IN_PROGRESS -> {
          StateLabel("Upload in progress...")
        }

        BackupUploadState.UPLOAD_DONE -> {
          StateLabel("Upload complete.")
        }

        BackupUploadState.UPLOAD_FAILED -> {
          StateLabel("Upload failed.")
        }
      }
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
  state: InternalBackupPlaygroundViewModel.MediaState,
  snackbarHostState: SnackbarHostState,
  backupAttachmentMedia: (InternalBackupPlaygroundViewModel.BackupAttachment) -> Unit,
  deleteBackupAttachmentMedia: (InternalBackupPlaygroundViewModel.BackupAttachment) -> Unit,
  batchBackupAttachmentMedia: (Set<String>) -> Unit,
  batchDeleteBackupAttachmentMedia: (Set<String>) -> Unit
) {
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
                  selectionState = selectionState.copy(selected = if (selectionState.selected.contains(attachment.mediaId)) selectionState.selected - attachment.mediaId else selectionState.selected + attachment.mediaId)
                }
              },
              onLongClick = {
                selectionState = if (selectionState.selecting) MediaMultiSelectState() else MediaMultiSelectState(selecting = true, selected = setOf(attachment.mediaId))
              }
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
          if (selectionState.selecting) {
            Checkbox(
              checked = selectionState.selected.contains(attachment.mediaId),
              onCheckedChange = { selected ->
                selectionState = selectionState.copy(selected = if (selected) selectionState.selected + attachment.mediaId else selectionState.selected - attachment.mediaId)
              }
            )
          }

          Column(modifier = Modifier.weight(1f, true)) {
            Text(text = "Attachment ${attachment.title}")
            Text(text = "State: ${attachment.state}")
          }

          if (attachment.state == InternalBackupPlaygroundViewModel.BackupAttachment.State.INIT ||
            attachment.state == InternalBackupPlaygroundViewModel.BackupAttachment.State.IN_PROGRESS
          ) {
            CircularProgressIndicator()
          } else {
            Button(
              enabled = !selectionState.selecting,
              onClick = {
                when (attachment.state) {
                  InternalBackupPlaygroundViewModel.BackupAttachment.State.LOCAL_ONLY -> backupAttachmentMedia(attachment)
                  InternalBackupPlaygroundViewModel.BackupAttachment.State.UPLOADED -> deleteBackupAttachmentMedia(attachment)
                  else -> throw AssertionError("Unsupported state: ${attachment.state}")
                }
              }
            ) {
              Text(
                text = when (attachment.state) {
                  InternalBackupPlaygroundViewModel.BackupAttachment.State.LOCAL_ONLY -> "Backup"
                  InternalBackupPlaygroundViewModel.BackupAttachment.State.UPLOADED -> "Remote Delete"
                  else -> throw AssertionError("Unsupported state: ${attachment.state}")
                }
              )
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
          batchBackupAttachmentMedia(selectionState.selected)
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
  val selected: Set<String> = emptySet()
)

@Preview(name = "Light Theme", group = "screen", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark Theme", group = "screen", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PreviewScreen() {
  SignalTheme {
    Surface {
      Screen(state = ScreenState(backupState = BackupState.NONE, plaintext = false))
    }
  }
}

@Preview(name = "Light Theme", group = "screen", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark Theme", group = "screen", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PreviewScreenExportInProgress() {
  SignalTheme {
    Surface {
      Screen(state = ScreenState(backupState = BackupState.EXPORT_IN_PROGRESS, plaintext = false))
    }
  }
}

@Preview(name = "Light Theme", group = "screen", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark Theme", group = "screen", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PreviewScreenExportDone() {
  SignalTheme {
    Surface {
      Screen(state = ScreenState(backupState = BackupState.EXPORT_DONE, plaintext = false))
    }
  }
}

@Preview(name = "Light Theme", group = "screen", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark Theme", group = "screen", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PreviewScreenImportInProgress() {
  SignalTheme {
    Surface {
      Screen(state = ScreenState(backupState = BackupState.IMPORT_IN_PROGRESS, plaintext = false))
    }
  }
}

@Preview(name = "Light Theme", group = "screen", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark Theme", group = "screen", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PreviewScreenUploadInProgress() {
  SignalTheme {
    Surface {
      Screen(state = ScreenState(uploadState = BackupUploadState.UPLOAD_IN_PROGRESS, plaintext = false))
    }
  }
}
