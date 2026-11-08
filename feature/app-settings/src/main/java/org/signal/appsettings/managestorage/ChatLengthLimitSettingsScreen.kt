/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.managestorage

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import org.signal.appsettings.R
import org.signal.appsettings.appSettingsScreen
import org.signal.appsettings.managestorage.ChatLengthLimitSettingsState.Dialog
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Rows.TextAndLabel
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import java.text.NumberFormat

@VisibleForTesting
object ChatLengthLimitSettingsTestTags {
  const val SCROLLER = "scroller"
  const val ROW_PRESET_PREFIX = "row-preset-"
  const val ROW_CUSTOM = "row-custom"
  const val FIELD_CUSTOM_LIMIT = "field-custom-limit"
  const val BUTTON_CUSTOM_LIMIT_OK = "button-custom-limit-ok"
  const val DIALOG_CONFIRM_LIMIT = "dialog-confirm-limit"
  const val DIALOG_CUSTOM_LIMIT = "dialog-custom-limit"
}

/**
 * Lets the user pick how many messages each chat is trimmed to.
 */
@Composable
fun ChatLengthLimitSettingsScreen(
  state: ChatLengthLimitSettingsState,
  onEvent: (ChatLengthLimitSettingsEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  Scaffolds.Settings(
    title = stringResource(R.string.preferences__conversation_length_limit),
    onNavigationClick = { onEvent(ChatLengthLimitSettingsEvent.NavigateBackClicked) },
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    modifier = modifier
  ) { contentPadding ->
    Column(
      modifier = Modifier
        .padding(contentPadding)
        .verticalScroll(rememberScrollState())
        .appSettingsScreen()
        .testTag(ChatLengthLimitSettingsTestTags.SCROLLER)
    ) {
      ChatLengthLimitSettingsState.PRESETS.forEach { preset ->
        Rows.RadioRow(
          text = lengthLimitLabel(preset),
          selected = preset == state.lengthLimit,
          modifier = Modifier
            .clickable { onEvent(ChatLengthLimitSettingsEvent.LimitSelected(preset)) }
            .testTag(ChatLengthLimitSettingsTestTags.ROW_PRESET_PREFIX + preset)
        )
      }

      CustomRow(state = state, onEvent = onEvent)

      ChatLimitWarning(text = stringResource(R.string.ManageStorageSettingsFragment_chat_length_limit_warning))
    }
  }

  when (val dialog = state.dialog) {
    Dialog.None -> Unit
    is Dialog.ConfirmLimit -> ConfirmLimitDialog(dialog = dialog, onEvent = onEvent)
    is Dialog.CustomLimit -> CustomLimitDialog(dialog = dialog, onEvent = onEvent)
  }
}

@Composable
private fun CustomRow(
  state: ChatLengthLimitSettingsState,
  onEvent: (ChatLengthLimitSettingsEvent) -> Unit
) {
  Rows.RadioRow(
    content = {
      TextAndLabel(
        text = stringResource(R.string.preferences_storage__custom),
        label = if (state.isCustom) lengthLimitLabel(state.lengthLimit) else null
      )

      if (state.isCustom) {
        Dividers.Vertical(
          modifier = Modifier
            .padding(horizontal = 8.dp)
            .height(48.dp)
        )

        Icon(
          painter = SignalIcons.Settings.painter,
          contentDescription = null,
          modifier = Modifier
            .clickable { onEvent(ChatLengthLimitSettingsEvent.CustomClicked) }
            .padding(12.dp)
        )
      }
    },
    selected = state.isCustom,
    modifier = Modifier
      .clickable { onEvent(ChatLengthLimitSettingsEvent.CustomClicked) }
      .testTag(ChatLengthLimitSettingsTestTags.ROW_CUSTOM)
  )
}

@Composable
private fun ConfirmLimitDialog(
  dialog: Dialog.ConfirmLimit,
  onEvent: (ChatLengthLimitSettingsEvent) -> Unit
) {
  Dialogs.SimpleAlertDialog(
    title = stringResource(R.string.preferences_storage__delete_older_messages),
    body = pluralStringResource(
      R.plurals.preferences_storage__this_will_permanently_trim_all_conversations_to_the_d_most_recent_messages,
      dialog.limit,
      NumberFormat.getInstance().format(dialog.limit)
    ),
    confirm = stringResource(R.string.preferences_storage__delete),
    dismiss = stringResource(android.R.string.cancel),
    onConfirm = { onEvent(ChatLengthLimitSettingsEvent.LimitConfirmed) },
    onDeny = { onEvent(ChatLengthLimitSettingsEvent.DialogDismissed) },
    onDismissRequest = { onEvent(ChatLengthLimitSettingsEvent.DialogDismissed) },
    modifier = Modifier.testTag(ChatLengthLimitSettingsTestTags.DIALOG_CONFIRM_LIMIT)
  )
}

@Composable
private fun CustomLimitDialog(
  dialog: Dialog.CustomLimit,
  onEvent: (ChatLengthLimitSettingsEvent) -> Unit
) {
  var lengthLimit by remember {
    val initialText = dialog.initialLimit?.toString().orEmpty()
    mutableStateOf(TextFieldValue(text = initialText, selection = TextRange(initialText.length)))
  }

  val focusRequester = remember { FocusRequester() }

  AlertDialog(
    onDismissRequest = { onEvent(ChatLengthLimitSettingsEvent.DialogDismissed) },
    title = { Text(text = stringResource(R.string.preferences__conversation_length_limit)) },
    text = {
      TextField(
        value = lengthLimit,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        onValueChange = { value ->
          val cleaned = value.text.replace("\\D".toRegex(), "")
          lengthLimit = if (cleaned == value.text) value else value.copy(text = cleaned)
        },
        modifier = Modifier
          .focusRequester(focusRequester)
          .testTag(ChatLengthLimitSettingsTestTags.FIELD_CUSTOM_LIMIT)
      )

      LaunchedEffect(Unit) {
        focusRequester.requestFocus()
      }
    },
    confirmButton = {
      val limit = lengthLimit.text.toIntOrNull()

      TextButton(
        enabled = limit != null,
        onClick = { limit?.let { onEvent(ChatLengthLimitSettingsEvent.CustomLimitEntered(it)) } },
        modifier = Modifier.testTag(ChatLengthLimitSettingsTestTags.BUTTON_CUSTOM_LIMIT_OK)
      ) {
        Text(text = stringResource(android.R.string.ok))
      }
    },
    dismissButton = {
      TextButton(onClick = { onEvent(ChatLengthLimitSettingsEvent.DialogDismissed) }) {
        Text(text = stringResource(android.R.string.cancel))
      }
    },
    modifier = Modifier.testTag(ChatLengthLimitSettingsTestTags.DIALOG_CUSTOM_LIMIT)
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun ChatLengthLimitSettingsScreenPreview() {
  ChatLengthLimitSettingsScreen(state = ChatLengthLimitSettingsState(lengthLimit = 1000), onEvent = {})
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun ChatLengthLimitSettingsScreenCustomPreview() {
  ChatLengthLimitSettingsScreen(state = ChatLengthLimitSettingsState(lengthLimit = 1234), onEvent = {})
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun ChatLengthLimitSettingsScreenCustomDialogPreview() {
  ChatLengthLimitSettingsScreen(state = ChatLengthLimitSettingsState(dialog = Dialog.CustomLimit(initialLimit = 123)), onEvent = {})
}
