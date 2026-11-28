/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.managestorage

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.lifecycle.compose.LifecycleResumeEffect
import org.signal.appsettings.R
import org.signal.appsettings.appSettingsScreen
import org.signal.appsettings.managestorage.ManageStorageSettingsState.Dialog
import org.signal.core.models.storage.KeepMessagesDuration
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.compose.Texts
import org.signal.core.ui.R as CoreUiR

@VisibleForTesting
object ManageStorageSettingsTestTags {
  const val SCROLLER = "scroller"
  const val BUTTON_REVIEW_STORAGE = "button-review-storage"
  const val ROW_OPTIMIZE_STORAGE = "row-optimize-storage"
  const val ROW_KEEP_MESSAGES = "row-keep-messages"
  const val ROW_CHAT_LENGTH_LIMIT = "row-chat-length-limit"
  const val ROW_SYNC_TRIM_DELETES = "row-sync-trim-deletes"
  const val ROW_DELETE_CHAT_HISTORY = "row-delete-chat-history"
  const val DIALOG_CONFIRM_DELETE_CHAT_HISTORY = "dialog-confirm-delete-chat-history"
  const val DIALOG_RECONFIRM_DELETE_CHAT_HISTORY = "dialog-reconfirm-delete-chat-history"
  const val DIALOG_PAID_TIER_PENDING = "dialog-paid-tier-pending"
  const val DIALOG_CONFIRM_OPTIMIZE_STORAGE_WITH_LOCAL_BACKUP = "dialog-confirm-optimize-storage-with-local-backup"
}

/**
 * Manage settings related to on-device storage including viewing usage and auto-delete settings.
 */
@Composable
fun ManageStorageSettingsScreen(
  state: ManageStorageSettingsState,
  onEvent: (ManageStorageSettingsEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  LifecycleResumeEffect(Unit) {
    onEvent(ManageStorageSettingsEvent.ScreenResumed)
    onPauseOrDispose {}
  }

  Scaffolds.Settings(
    title = stringResource(R.string.preferences__storage),
    onNavigationClick = { onEvent(ManageStorageSettingsEvent.NavigateBackClicked) },
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    navigationContentDescription = stringResource(CoreUiR.string.CloseButton__content_description),
    modifier = modifier,
    expandableTitle = true
  ) { contentPadding ->
    Column(
      modifier = Modifier
        .padding(contentPadding)
        .verticalScroll(rememberScrollState())
        .appSettingsScreen()
        .testTag(ManageStorageSettingsTestTags.SCROLLER)
    ) {
      Texts.SectionHeader(text = stringResource(R.string.preferences_storage__storage_usage))

      StorageUsageOverview(
        usage = state.storageUsage,
        onReviewStorage = { onEvent(ManageStorageSettingsEvent.ReviewStorageClicked) }
      )

      if (state.isOptimizeStorageVisible) {
        Dividers.Default()

        Texts.SectionHeader(text = stringResource(R.string.ManageStorageSettingsFragment__on_device_storage))

        Rows.ToggleRow(
          checked = state.isOptimizeStorageChecked,
          text = stringResource(R.string.ManageStorageSettingsFragment__optimize_on_device_storage),
          label = stringResource(R.string.ManageStorageSettingsFragment__unused_media_will_be_offloaded),
          onCheckChanged = { onEvent(ManageStorageSettingsEvent.OptimizeStorageToggled(it)) },
          modifier = Modifier.testTag(ManageStorageSettingsTestTags.ROW_OPTIMIZE_STORAGE)
        )
      }

      Dividers.Default()

      Texts.SectionHeader(text = stringResource(R.string.ManageStorageSettingsFragment_chat_limit))

      Rows.TextRow(
        text = stringResource(R.string.preferences__keep_messages),
        label = stringResource(state.keepMessagesDuration.labelRes),
        onClick = { onEvent(ManageStorageSettingsEvent.KeepMessagesClicked) },
        modifier = Modifier.testTag(ManageStorageSettingsTestTags.ROW_KEEP_MESSAGES)
      )

      Rows.TextRow(
        text = stringResource(R.string.preferences__conversation_length_limit),
        label = lengthLimitLabel(state.lengthLimit),
        onClick = { onEvent(ManageStorageSettingsEvent.ChatLengthLimitClicked) },
        modifier = Modifier.testTag(ManageStorageSettingsTestTags.ROW_CHAT_LENGTH_LIMIT)
      )

      if (state.isPrimaryDevice) {
        Rows.ToggleRow(
          text = stringResource(R.string.ManageStorageSettingsFragment_apply_limits_title),
          label = stringResource(R.string.ManageStorageSettingsFragment_apply_limits_description),
          checked = state.syncTrimDeletes,
          onCheckChanged = { onEvent(ManageStorageSettingsEvent.SyncTrimDeletesToggled(it)) },
          modifier = Modifier.testTag(ManageStorageSettingsTestTags.ROW_SYNC_TRIM_DELETES)
        )
      }

      Dividers.Default()

      Rows.TextRow(
        text = stringResource(R.string.ManageStorageSettingsFragment_delete_message_history),
        onClick = { onEvent(ManageStorageSettingsEvent.DeleteChatHistoryClicked) },
        modifier = Modifier.testTag(ManageStorageSettingsTestTags.ROW_DELETE_CHAT_HISTORY)
      )
    }
  }

  when (state.dialog) {
    Dialog.None -> Unit
    Dialog.ConfirmDeleteChatHistory -> ConfirmDeleteChatHistoryDialog(isMultiDevice = state.isMultiDevice, onEvent = onEvent)
    Dialog.ReconfirmDeleteChatHistory -> ReconfirmDeleteChatHistoryDialog(isMultiDevice = state.isMultiDevice, onEvent = onEvent)
    Dialog.PaidTierPending -> PaidTierPendingDialog(onEvent = onEvent)
    Dialog.ConfirmOptimizeStorageWithLocalBackup -> ConfirmOptimizeStorageWithLocalBackupDialog(onEvent = onEvent)
  }
}

@Composable
private fun ConfirmDeleteChatHistoryDialog(
  isMultiDevice: Boolean,
  onEvent: (ManageStorageSettingsEvent) -> Unit
) {
  Dialogs.SimpleAlertDialog(
    title = stringResource(R.string.preferences_storage__delete_message_history),
    body = if (isMultiDevice) {
      stringResource(R.string.preferences_storage__this_will_delete_all_message_history_and_media_from_your_device_linked_device)
    } else {
      stringResource(R.string.preferences_storage__this_will_delete_all_message_history_and_media_from_your_device)
    },
    confirm = stringResource(R.string.preferences_storage__delete),
    confirmColor = MaterialTheme.colorScheme.error,
    dismiss = stringResource(android.R.string.cancel),
    onConfirm = { onEvent(ManageStorageSettingsEvent.DeleteChatHistoryConfirmed) },
    onDeny = { onEvent(ManageStorageSettingsEvent.DialogDismissed) },
    onDismissRequest = { onEvent(ManageStorageSettingsEvent.DialogDismissed) },
    modifier = Modifier.testTag(ManageStorageSettingsTestTags.DIALOG_CONFIRM_DELETE_CHAT_HISTORY)
  )
}

@Composable
private fun ReconfirmDeleteChatHistoryDialog(
  isMultiDevice: Boolean,
  onEvent: (ManageStorageSettingsEvent) -> Unit
) {
  Dialogs.SimpleAlertDialog(
    title = stringResource(R.string.preferences_storage__are_you_sure_you_want_to_delete_all_message_history),
    body = if (isMultiDevice) {
      stringResource(R.string.preferences_storage__all_message_history_will_be_permanently_removed_this_action_cannot_be_undone_linked_device)
    } else {
      stringResource(R.string.preferences_storage__all_message_history_will_be_permanently_removed_this_action_cannot_be_undone)
    },
    confirm = stringResource(R.string.preferences_storage__delete_all_now),
    confirmColor = MaterialTheme.colorScheme.error,
    dismiss = stringResource(android.R.string.cancel),
    onConfirm = { onEvent(ManageStorageSettingsEvent.DeleteChatHistoryConfirmed) },
    onDeny = { onEvent(ManageStorageSettingsEvent.DialogDismissed) },
    onDismissRequest = { onEvent(ManageStorageSettingsEvent.DialogDismissed) },
    modifier = Modifier.testTag(ManageStorageSettingsTestTags.DIALOG_RECONFIRM_DELETE_CHAT_HISTORY)
  )
}

@Composable
private fun PaidTierPendingDialog(
  onEvent: (ManageStorageSettingsEvent) -> Unit
) {
  Dialogs.SimpleAlertDialog(
    title = "",
    body = stringResource(R.string.ManageStorageSettingsFragment__storage_optimization_can_only_be_used),
    confirm = stringResource(android.R.string.ok),
    onConfirm = { onEvent(ManageStorageSettingsEvent.DialogDismissed) },
    onDismissRequest = { onEvent(ManageStorageSettingsEvent.DialogDismissed) },
    modifier = Modifier.testTag(ManageStorageSettingsTestTags.DIALOG_PAID_TIER_PENDING)
  )
}

@Composable
private fun ConfirmOptimizeStorageWithLocalBackupDialog(
  onEvent: (ManageStorageSettingsEvent) -> Unit
) {
  Dialogs.SimpleAlertDialog(
    title = stringResource(R.string.ManageStorageSettingsFragment__media_will_be_removed_from_your_on_device_backup),
    body = stringResource(R.string.ManageStorageSettingsFragment__turning_on_optimize_signal_storage_will_offload),
    confirm = stringResource(R.string.ManageStorageSettingsFragment__turn_on),
    dismiss = stringResource(android.R.string.cancel),
    onConfirm = { onEvent(ManageStorageSettingsEvent.OptimizeStorageWithLocalBackupConfirmed) },
    onDeny = { onEvent(ManageStorageSettingsEvent.DialogDismissed) },
    onDismissRequest = { onEvent(ManageStorageSettingsEvent.DialogDismissed) },
    modifier = Modifier.testTag(ManageStorageSettingsTestTags.DIALOG_CONFIRM_OPTIMIZE_STORAGE_WITH_LOCAL_BACKUP)
  )
}

private val previewState = ManageStorageSettingsState(
  storageUsage = StorageUsage(
    photos = 1_200_000_000,
    videos = 3_400_000_000,
    files = 150_000_000,
    audio = 80_000_000,
    messages = 2_100_000_000,
    stickers = 120_000_000,
    other = 900_000_000
  ),
  onDeviceStorageOptimizationState = ManageStorageSettingsState.OnDeviceStorageOptimizationState.DISABLED,
  keepMessagesDuration = KeepMessagesDuration.ONE_YEAR,
  lengthLimit = 1000,
  syncTrimDeletes = true
)

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun ManageStorageSettingsScreenPreview() {
  ManageStorageSettingsScreen(state = previewState, onEvent = {})
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun ManageStorageSettingsScreenCalculatingPreview() {
  ManageStorageSettingsScreen(state = ManageStorageSettingsState(), onEvent = {})
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun ManageStorageSettingsScreenConfirmDeletePreview() {
  ManageStorageSettingsScreen(state = previewState.copy(dialog = Dialog.ConfirmDeleteChatHistory), onEvent = {})
}
