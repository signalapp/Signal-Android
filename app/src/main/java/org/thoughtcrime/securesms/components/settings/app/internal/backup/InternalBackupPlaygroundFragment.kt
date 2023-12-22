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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import org.signal.core.ui.Buttons
import org.signal.core.ui.Dividers
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
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state

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
      onCheckRemoteBackupStateClicked = { viewModel.checkRemoteBackupState() }
    )
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
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
          StateLabel("Exists/allocated. Space used by media: ${state.remoteBackupState.response.usedSpace ?: 0} bytes (${state.remoteBackupState.response.usedSpace?.bytes?.inMebiBytes?.roundedString(3) ?: 0} MiB)")
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
