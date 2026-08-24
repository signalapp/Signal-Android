/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit

import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.signal.mediasend.R
import kotlin.time.Duration.Companion.milliseconds

object MediaEditScreenDialogs {

  @Composable
  fun DiscardEditsConfirmationDialog(
    onDiscard: () -> Unit,
    onDismiss: () -> Unit
  ) {
    Dialogs.SimpleAlertDialog(
      title = stringResource(R.string.MediaSendDialogs__discard_changes),
      body = stringResource(R.string.MediaSendDialogs__youll_lose_any_changes),
      confirm = stringResource(R.string.MediaSendDialogs__discard),
      onConfirm = onDiscard,
      dismiss = stringResource(android.R.string.cancel),
      onDismiss = onDismiss
    )
  }

  /**
   * Last chance to back out before the media is posted to [groupName]'s story.
   */
  @Composable
  fun AddToGroupStoryConfirmationDialog(
    groupName: String,
    onAddToStory: () -> Unit,
    onDeny: () -> Unit,
    onDismissRequest: () -> Unit
  ) {
    Dialogs.SimpleAlertDialog(
      title = "",
      body = stringResource(R.string.MediaSendDialogs__add_to_the_group_story, groupName),
      confirm = stringResource(R.string.MediaSendDialogs__add_to_story),
      onConfirm = onAddToStory,
      dismiss = stringResource(android.R.string.cancel),
      onDeny = onDeny,
      onDismissRequest = onDismissRequest
    )
  }

  @Composable
  fun SaveToStorageConfirmationDialog(
    onSave: (doNotShowAgain: Boolean) -> Unit,
    onDismissRequest: () -> Unit
  ) {
    var doNotShowAgain by remember { mutableStateOf(false) }

    Dialogs.BaseAlertDialog(
      onDismissRequest = onDismissRequest,
      title = { Text(text = stringResource(R.string.MediaSendDialogs__save_to_phone)) },
      text = {
        Column(verticalArrangement = spacedBy(16.dp)) {
          Text(text = stringResource(R.string.MediaSendDialogs__this_media_will_be_saved))

          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
              .fillMaxWidth()
              .toggleable(
                value = doNotShowAgain,
                role = Role.Checkbox,
                onValueChange = { doNotShowAgain = it }
              )
          ) {
            Checkbox(
              checked = doNotShowAgain,
              onCheckedChange = null
            )

            Text(
              text = stringResource(R.string.MediaSendDialogs__dont_show_again),
              modifier = Modifier.padding(start = 16.dp)
            )
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { onSave(doNotShowAgain) }) {
          Text(text = stringResource(R.string.MediaSendDialogs__save))
        }
      },
      dismissButton = {
        TextButton(onClick = onDismissRequest) {
          Text(text = stringResource(android.R.string.cancel))
        }
      },
      modifier = Modifier
    )
  }

  @Composable
  fun SavingToStorageProgressDialog() {
    Dialogs.IndeterminateProgressDialog(message = stringResource(R.string.MediaSendDialogs__saving_media))
  }

  /**
   * Covers face detection, which blocks the editor for as long as it runs. Detection on a small image can finish in a
   * frame or two, so the spinner waits before showing itself rather than flashing.
   */
  @Composable
  fun DetectingFacesProgressDialog(visible: Boolean) {
    Dialogs.IndeterminateProgressDialog(
      visible = visible,
      delayDuration = 200.milliseconds,
      minimumDisplayDuration = 400.milliseconds
    )
  }
}

@Preview
@Composable
private fun DiscardEditsConfirmationDialogPreview() {
  Previews.Preview {
    MediaEditScreenDialogs.DiscardEditsConfirmationDialog(
      onDiscard = {},
      onDismiss = {}
    )
  }
}

@Preview
@Composable
private fun AddToGroupStoryConfirmationDialogPreview() {
  Previews.Preview {
    MediaEditScreenDialogs.AddToGroupStoryConfirmationDialog(
      groupName = "Signal Android",
      onAddToStory = {},
      onDeny = {},
      onDismissRequest = {}
    )
  }
}

@Preview
@Composable
private fun SaveToStorageConfirmationDialogPreview() {
  Previews.Preview {
    MediaEditScreenDialogs.SaveToStorageConfirmationDialog(
      onSave = {},
      onDismissRequest = {}
    )
  }
}

@Preview
@Composable
private fun SavingToStorageProgressDialogPreview() {
  Previews.Preview {
    MediaEditScreenDialogs.SavingToStorageProgressDialog()
  }
}
