/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.internal.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.tooling.preview.Preview
import androidx.fragment.app.viewModels
import org.signal.core.ui.Buttons
import org.thoughtcrime.securesms.components.settings.app.internal.backup.InternalBackupPlaygroundViewModel.BackupState
import org.thoughtcrime.securesms.components.settings.app.internal.backup.InternalBackupPlaygroundViewModel.ScreenState
import org.thoughtcrime.securesms.compose.ComposeFragment

class InternalBackupPlaygroundFragment : ComposeFragment() {

  val viewModel: InternalBackupPlaygroundViewModel by viewModels()

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state

    Screen(
      state = state,
      onExportClicked = { viewModel.export() },
      onImportClicked = { viewModel.import() }
    )
  }
}

@Composable
fun Screen(
  state: ScreenState,
  onExportClicked: () -> Unit = {},
  onImportClicked: () -> Unit = {}
) {
  Surface {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Buttons.LargePrimary(
        onClick = onExportClicked,
        enabled = !state.backupState.inProgress
      ) {
        Text("Export")
      }
      Buttons.LargeTonal(
        onClick = onImportClicked,
        enabled = state.backupState == BackupState.EXPORT_DONE
      ) {
        Text("Import")
      }
    }
  }
}

@Preview
@Composable
fun PreviewScreen() {
  Screen(state = ScreenState(backupState = BackupState.NONE))
}
