/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation.shared

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.signal.core.ui.compose.Dialogs
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.conversation.preferences.Utils.formatMutedUntil
import org.thoughtcrime.securesms.recipients.Recipient

/** Asks the user to confirm unmuting a chat, telling them how long it would otherwise stay muted. */
@Composable
fun UnmuteDialog(
  recipient: Recipient,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current

  Dialogs.SimpleAlertDialog(
    title = "",
    body = recipient.muteUntil.formatMutedUntil(context),
    confirm = stringResource(R.string.ConversationSettingsFragment__unmute),
    dismiss = stringResource(android.R.string.cancel),
    onConfirm = onConfirm,
    onDismiss = onDismiss
  )
}
