/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.signal.core.ui.DarkPreview
import org.signal.core.ui.Dialogs
import org.signal.core.ui.Previews
import org.thoughtcrime.securesms.R

/**
 * Displays the current dialog to the user, or nothing.
 */
@Composable
fun CallScreenDialog(
  callScreenDialogType: CallScreenDialogType,
  onDialogDismissed: () -> Unit
) {
  when (callScreenDialogType) {
    CallScreenDialogType.NONE -> return
    CallScreenDialogType.REMOVED_FROM_CALL_LINK -> RemovedFromCallLinkDialog(onDialogDismissed)
    CallScreenDialogType.DENIED_REQUEST_TO_JOIN_CALL_LINK -> DeniedRequestToJoinCallDialog(onDialogDismissed)
  }
}

@Composable
private fun RemovedFromCallLinkDialog(onDialogDismissed: () -> Unit = {}) {
  Dialogs.SimpleAlertDialog(
    title = stringResource(R.string.WebRtcCallActivity__removed_from_call),
    body = stringResource(R.string.WebRtcCallActivity__someone_has_removed_you_from_the_call),
    confirm = stringResource(android.R.string.ok),
    onConfirm = {},
    onDismiss = onDialogDismissed
  )
}

@Composable
private fun DeniedRequestToJoinCallDialog(onDialogDismissed: () -> Unit = {}) {
  Dialogs.SimpleAlertDialog(
    title = stringResource(R.string.WebRtcCallActivity__join_request_denied),
    body = stringResource(R.string.WebRtcCallActivity__your_request_to_join_this_call_has_been_denied),
    confirm = stringResource(android.R.string.ok),
    onConfirm = {},
    onDismiss = onDialogDismissed
  )
}

@DarkPreview
@Composable
private fun RemovedFromCallLinkDialogPreview() {
  Previews.Preview {
    RemovedFromCallLinkDialog()
  }
}

@DarkPreview
@Composable
private fun DeniedRequestToJoinCallDialogPreview() {
  Previews.Preview {
    DeniedRequestToJoinCallDialog()
  }
}

/**
 * Enumeration of available call screen dialog types.
 */
enum class CallScreenDialogType {
  NONE,
  REMOVED_FROM_CALL_LINK,
  DENIED_REQUEST_TO_JOIN_CALL_LINK
}
