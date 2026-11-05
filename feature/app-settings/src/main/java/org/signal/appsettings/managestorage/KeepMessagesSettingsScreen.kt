/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.managestorage

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewWrapper
import org.signal.appsettings.R
import org.signal.appsettings.appSettingsScreen
import org.signal.core.models.storage.KeepMessagesDuration
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.R as CoreUiR

@VisibleForTesting
object KeepMessagesSettingsTestTags {
  const val SCROLLER = "scroller"
  const val ROW_DURATION_PREFIX = "row-duration-"
  const val DIALOG_CONFIRM_DURATION = "dialog-confirm-duration"
}

/**
 * Lets the user pick how long messages are kept before they're automatically deleted.
 */
@Composable
fun KeepMessagesSettingsScreen(
  state: KeepMessagesSettingsState,
  onEvent: (KeepMessagesSettingsEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  Scaffolds.Settings(
    title = stringResource(R.string.preferences__keep_messages),
    onNavigationClick = { onEvent(KeepMessagesSettingsEvent.NavigateBackClicked) },
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    navigationContentDescription = stringResource(CoreUiR.string.CloseButton__content_description),
    modifier = modifier
  ) { contentPadding ->
    Column(
      modifier = Modifier
        .padding(contentPadding)
        .verticalScroll(rememberScrollState())
        .appSettingsScreen()
        .testTag(KeepMessagesSettingsTestTags.SCROLLER)
    ) {
      KeepMessagesDuration.entries.forEach { duration ->
        Rows.RadioRow(
          text = stringResource(duration.labelRes),
          selected = duration == state.selection,
          modifier = Modifier
            .clickable { onEvent(KeepMessagesSettingsEvent.DurationSelected(duration)) }
            .testTag(KeepMessagesSettingsTestTags.ROW_DURATION_PREFIX + duration.name)
        )
      }

      ChatLimitWarning(text = stringResource(R.string.ManageStorageSettingsFragment_keep_messages_duration_warning))
    }
  }

  when (val dialog = state.dialog) {
    KeepMessagesSettingsState.Dialog.None -> Unit
    is KeepMessagesSettingsState.Dialog.ConfirmDuration -> ConfirmDurationDialog(dialog = dialog, onEvent = onEvent)
  }
}

@Composable
private fun ConfirmDurationDialog(
  dialog: KeepMessagesSettingsState.Dialog.ConfirmDuration,
  onEvent: (KeepMessagesSettingsEvent) -> Unit
) {
  Dialogs.SimpleAlertDialog(
    title = stringResource(R.string.preferences_storage__delete_older_messages),
    body = stringResource(R.string.preferences_storage__this_will_permanently_delete_all_message_history_and_media, stringResource(dialog.duration.labelRes)),
    confirm = stringResource(R.string.preferences_storage__delete),
    dismiss = stringResource(android.R.string.cancel),
    onConfirm = { onEvent(KeepMessagesSettingsEvent.DurationConfirmed) },
    onDeny = { onEvent(KeepMessagesSettingsEvent.DialogDismissed) },
    onDismissRequest = { onEvent(KeepMessagesSettingsEvent.DialogDismissed) },
    modifier = Modifier.testTag(KeepMessagesSettingsTestTags.DIALOG_CONFIRM_DURATION)
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun KeepMessagesSettingsScreenPreview() {
  KeepMessagesSettingsScreen(state = KeepMessagesSettingsState(selection = KeepMessagesDuration.ONE_YEAR), onEvent = {})
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun KeepMessagesSettingsScreenConfirmPreview() {
  KeepMessagesSettingsScreen(
    state = KeepMessagesSettingsState(dialog = KeepMessagesSettingsState.Dialog.ConfirmDuration(KeepMessagesDuration.THIRTY_DAYS)),
    onEvent = {}
  )
}
