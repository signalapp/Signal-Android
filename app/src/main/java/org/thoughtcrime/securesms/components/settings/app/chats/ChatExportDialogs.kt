/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.chats

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Launchers
import org.thoughtcrime.securesms.R

/**
 * Dialogs displayed while processing a user's decrypted chat export.
 *
 * Displayed *after* the user has confirmed via phone auth.
 */
@Composable
fun ChatExportDialogs(state: ChatExportState, callbacks: ChatExportCallbacks) {
  val folderPicker = Launchers.rememberOpenDocumentTreeLauncher {
    if (it != null) {
      callbacks.onFolderSelected(it)
    } else {
      callbacks.onCancelStartExport()
    }
  }

  when (state) {
    ChatExportState.None -> Unit
    ChatExportState.ConfirmExport -> ConfirmExportDialog(
      onConfirmExport = callbacks::onConfirmExport,
      onCancel = callbacks::onCancelStartExport
    )

    ChatExportState.ChooseAFolder -> ChooseAFolderDialog(
      onChooseAFolder = { folderPicker.launch(null) },
      onCancel = callbacks::onCancelStartExport
    )

    ChatExportState.Canceling -> Dialogs.IndeterminateProgressDialog(message = stringResource(R.string.ChatExportDialogs__canceling_export))

    ChatExportState.Success -> CompleteDialog(
      onOK = callbacks::onCompletionConfirmed
    )
  }
}

@Composable
private fun ConfirmExportDialog(
  onConfirmExport: (withMedia: Boolean) -> Unit,
  onCancel: () -> Unit
) {
  val body = buildAnnotatedString {
    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
      append(stringResource(R.string.ChatExportDialogs__be_careful_warning))
    }

    append(" ")
    append(stringResource(R.string.ChatExportDialogs__export_confirm_body))
  }

  Dialogs.AdvancedAlertDialog(
    title = AnnotatedString(stringResource(R.string.ChatExportDialogs__export_chat_history_title)),
    body = body,
    positive = AnnotatedString(stringResource(R.string.ChatExportDialogs__export_with_media)),
    neutral = AnnotatedString(stringResource(R.string.ChatExportDialogs__export_without_media)),
    negative = AnnotatedString(stringResource(android.R.string.cancel)),
    onPositive = { onConfirmExport(true) },
    onNeutral = { onConfirmExport(false) },
    onNegative = onCancel
  )
}

@Composable
private fun ChooseAFolderDialog(
  onChooseAFolder: () -> Unit,
  onCancel: () -> Unit
) {
  Dialogs.SimpleAlertDialog(
    title = stringResource(R.string.ChatExportDialogs__choose_a_folder_title),
    body = stringResource(R.string.ChatExportDialogs__choose_a_folder_body),
    confirm = stringResource(R.string.ChatExportDialogs__choose_folder_button),
    dismiss = stringResource(android.R.string.cancel),
    onConfirm = onChooseAFolder,
    onDeny = onCancel
  )
}

@Composable
private fun CompleteDialog(
  onOK: () -> Unit
) {
  val body = buildAnnotatedString {
    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
      append(stringResource(R.string.ChatExportDialogs__be_careful))
    }

    append(" ")
    append(stringResource(R.string.ChatExportDialogs__complete_body))
  }

  Dialogs.SimpleAlertDialog(
    title = AnnotatedString(stringResource(R.string.ChatExportDialogs__complete_title)),
    body = body,
    confirm = AnnotatedString(stringResource(android.R.string.ok)),
    onConfirm = onOK
  )
}

enum class ChatExportState {
  None,
  ConfirmExport,
  ChooseAFolder,
  Canceling,
  Success
}

interface ChatExportCallbacks {
  fun onConfirmExport(withMedia: Boolean)
  fun onFolderSelected(uri: Uri)
  fun onCancelStartExport()
  fun onCompletionConfirmed()

  object Empty : ChatExportCallbacks {
    override fun onConfirmExport(withMedia: Boolean) = Unit
    override fun onFolderSelected(uri: Uri) = Unit
    override fun onCancelStartExport() = Unit
    override fun onCompletionConfirmed() = Unit
  }
}
