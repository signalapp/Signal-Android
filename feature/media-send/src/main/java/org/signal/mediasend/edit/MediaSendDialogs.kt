/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.edit

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.signal.mediasend.R

object MediaSendDialogs {

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
}

@Preview
@Composable
private fun DiscardEditsConfirmationDialogPreview() {
  Previews.Preview {
    MediaSendDialogs.DiscardEditsConfirmationDialog(
      onDiscard = {},
      onDismiss = {}
    )
  }
}
