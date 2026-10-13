/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.NightPreview
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.permissions.Permissions
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
    CallScreenDialogType.BACKGROUND_RESTRICTED -> BackgroundRestrictedDialog(onDialogDismissed)
    CallScreenDialogType.MICROPHONE_SILENCED_IN_BACKGROUND -> MicrophoneSilencedInBackgroundDialog(onDialogDismissed)
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

@Composable
private fun BackgroundRestrictedDialog(onDialogDismissed: () -> Unit = {}) {
  OpenAppSettingsDialog(
    title = stringResource(R.string.EnableCallNotificationSettingsDialog__enable_background_activity),
    body = stringResource(R.string.WebRtcCallActivity__others_may_not_be_able_to_hear_you_when_your_screen_turns_off),
    onDialogDismissed = onDialogDismissed
  )
}

@Composable
private fun MicrophoneSilencedInBackgroundDialog(onDialogDismissed: () -> Unit = {}) {
  OpenAppSettingsDialog(
    title = stringResource(R.string.WebRtcCallActivity__others_couldnt_hear_you),
    body = stringResource(R.string.WebRtcCallActivity__your_microphone_was_muted_while_signal_was_in_the_background),
    onDialogDismissed = onDialogDismissed
  )
}

@Composable
private fun OpenAppSettingsDialog(
  title: String,
  body: String,
  onDialogDismissed: () -> Unit
) {
  val context = LocalContext.current

  Dialogs.SimpleAlertDialog(
    title = title,
    body = body,
    confirm = stringResource(R.string.EnableCallNotificationSettingsDialog__settings),
    dismiss = stringResource(R.string.WebRtcCallActivity__not_now),
    onConfirm = { context.startActivity(Permissions.getApplicationSettingsIntent(context)) },
    onDismiss = onDialogDismissed
  )
}

@NightPreview
@Composable
private fun RemovedFromCallLinkDialogPreview() {
  Previews.Preview {
    RemovedFromCallLinkDialog()
  }
}

@NightPreview
@Composable
private fun DeniedRequestToJoinCallDialogPreview() {
  Previews.Preview {
    DeniedRequestToJoinCallDialog()
  }
}

@NightPreview
@Composable
private fun BackgroundRestrictedDialogPreview() {
  Previews.Preview {
    BackgroundRestrictedDialog()
  }
}

@NightPreview
@Composable
private fun MicrophoneSilencedInBackgroundDialogPreview() {
  Previews.Preview {
    MicrophoneSilencedInBackgroundDialog()
  }
}

/**
 * Enumeration of available call screen dialog types.
 */
enum class CallScreenDialogType {
  NONE,
  REMOVED_FROM_CALL_LINK,
  DENIED_REQUEST_TO_JOIN_CALL_LINK,
  BACKGROUND_RESTRICTED,
  MICROPHONE_SILENCED_IN_BACKGROUND
}
