/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation.permissions

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewWrapper
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Rows.TextAndLabel
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.compose.Snackbars
import org.signal.core.ui.compose.showSnackbar
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.conversation.permissions.PermissionsSettingsState.Dialog
import org.thoughtcrime.securesms.groups.ui.GroupChangeFailureReason
import org.thoughtcrime.securesms.groups.ui.GroupErrors

/** Row values, in the order [R.array.PermissionsSettingsFragment__editor_labels] declares their labels. */
private const val VALUE_ONLY_ADMINS = "only_admins"
private const val VALUE_ALL_MEMBERS = "all_members"
private val EDITOR_VALUES = arrayOf(VALUE_ONLY_ADMINS, VALUE_ALL_MEMBERS)

/**
 * Lets a group admin choose which of the group's actions non-admins are allowed to take.
 */
@Composable
fun PermissionsSettingsScreen(
  state: PermissionsSettingsState,
  onEvent: (PermissionsSettingsEvents) -> Unit,
  onNavigationClick: () -> Unit
) {
  val editorLabels = stringArrayResource(R.array.PermissionsSettingsFragment__editor_labels)

  Scaffolds.Settings(
    title = stringResource(R.string.ConversationSettingsFragment__permissions),
    onNavigationClick = onNavigationClick,
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    navigationContentDescription = stringResource(R.string.CallScreenTopBar__go_back),
    snackbarHost = {
      GroupChangeErrorSnackbarHost(
        groupChangeError = state.groupChangeError,
        onDismiss = { onEvent(PermissionsSettingsEvents.SnackbarDismissed) }
      )
    }
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .padding(paddingValues)
        .testTag(PermissionsSettingsTestTags.CONTENT)
    ) {
      item {
        PermissionRow(
          text = stringResource(R.string.PermissionsSettingsFragment__add_members),
          dialogTitle = stringResource(R.string.PermissionsSettingsFragment__who_can_add_new_members),
          labels = editorLabels,
          nonAdminAllowed = state.nonAdminCanAddMembers,
          onSelected = { onEvent(PermissionsSettingsEvents.SetNonAdminCanAddMembers(it)) },
          modifier = Modifier.testTag(PermissionsSettingsTestTags.ADD_MEMBERS_ROW),
          enabled = state.selfCanEditSettings
        )
      }

      item {
        PermissionRow(
          text = stringResource(R.string.PermissionsSettingsFragment__edit_group_info),
          dialogTitle = stringResource(R.string.PermissionsSettingsFragment__who_can_edit_this_groups_info),
          labels = editorLabels,
          nonAdminAllowed = state.nonAdminCanEditGroupInfo,
          onSelected = { onEvent(PermissionsSettingsEvents.SetNonAdminCanEditGroupInfo(it)) },
          modifier = Modifier.testTag(PermissionsSettingsTestTags.EDIT_GROUP_INFO_ROW),
          enabled = state.selfCanEditSettings
        )
      }

      item {
        PermissionRow(
          text = stringResource(R.string.PermissionsSettingsFragment__send_messages),
          dialogTitle = stringResource(R.string.PermissionsSettingsFragment__who_can_send_messages),
          labels = editorLabels,
          nonAdminAllowed = state.nonAdminCanSendMessages,
          onSelected = { onEvent(PermissionsSettingsEvents.SetNonAdminCanSendMessages(it)) },
          modifier = Modifier.testTag(PermissionsSettingsTestTags.SEND_MESSAGES_ROW),
          enabled = state.selfCanEditSettings
        )
      }

      item {
        PermissionRow(
          text = stringResource(R.string.PermissionsSettingsFragment__add_member_labels),
          dialogTitle = stringResource(R.string.PermissionsSettingsFragment__who_can_add_member_labels),
          labels = editorLabels,
          nonAdminAllowed = state.nonAdminCanSetMemberLabel,
          onSelected = { onEvent(PermissionsSettingsEvents.SetNonAdminCanSetMemberLabel(it)) },
          modifier = Modifier.testTag(PermissionsSettingsTestTags.ADD_MEMBER_LABELS_ROW),
          enabled = state.selfCanEditSettings
        )
      }
    }
  }

  PermissionsSettingsDialogs(
    state = state,
    onEvent = onEvent
  )
}

/**
 * A single permission, whose editors are either all members or admins only.
 *
 * Every one of these is a change to the whole group, so the user has to confirm their choice before we apply it.
 */
@Composable
private fun PermissionRow(
  text: String,
  dialogTitle: String,
  labels: Array<String>,
  nonAdminAllowed: Boolean,
  onSelected: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true
) {
  Rows.RadioListRow(
    text = { selectedIndex ->
      TextAndLabel(
        text = text,
        label = labels.getOrNull(selectedIndex)
      )
    },
    dialogTitle = dialogTitle,
    labels = labels,
    values = EDITOR_VALUES,
    selectedValue = if (nonAdminAllowed) VALUE_ALL_MEMBERS else VALUE_ONLY_ADMINS,
    onSelected = { onSelected(it == VALUE_ALL_MEMBERS) },
    modifier = modifier,
    enabled = enabled,
    requireConfirmation = true
  )
}

/**
 * Reports a rejected change, and tells the view model once it's been seen so the next rejection can show.
 */
@Composable
private fun GroupChangeErrorSnackbarHost(
  groupChangeError: GroupChangeFailureReason?,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  val hostState = remember { SnackbarHostState() }
  val message = groupChangeError?.let { stringResource(GroupErrors.getUserDisplayMessage(it)) }

  LaunchedEffect(groupChangeError) {
    if (message != null) {
      hostState.showSnackbar(message = message, duration = Snackbars.Duration.LONG)
      onDismiss()
    }
  }

  Snackbars.Host(hostState, modifier = modifier)
}

@Composable
private fun PermissionsSettingsDialogs(
  state: PermissionsSettingsState,
  onEvent: (PermissionsSettingsEvents) -> Unit
) {
  when (state.dialog) {
    Dialog.MemberLabelsWillBeCleared -> Dialogs.SimpleAlertDialog(
      title = stringResource(R.string.PermissionsSettingsFragment__member_labels_will_be_cleared_title),
      body = stringResource(R.string.PermissionsSettingsFragment__member_labels_will_be_cleared_body),
      confirm = stringResource(R.string.PermissionsSettingsFragment__change_permission),
      dismiss = stringResource(android.R.string.cancel),
      onConfirm = { onEvent(PermissionsSettingsEvents.MemberLabelsWillBeClearedConfirmed) },
      onDismiss = { onEvent(PermissionsSettingsEvents.DialogDismissed) }
    )

    Dialog.None -> Unit
  }
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun PermissionsSettingsScreenPreview() {
  PermissionsSettingsScreen(
    state = PermissionsSettingsState(
      selfCanEditSettings = true,
      nonAdminCanAddMembers = true,
      nonAdminCanSendMessages = true,
      nonAdminCanSetMemberLabel = true
    ),
    onEvent = {},
    onNavigationClick = {}
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun PermissionsSettingsScreenNonAdminPreview() {
  PermissionsSettingsScreen(
    state = PermissionsSettingsState(
      selfCanEditSettings = false,
      nonAdminCanAddMembers = true,
      nonAdminCanSendMessages = true
    ),
    onEvent = {},
    onNavigationClick = {}
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun PermissionsSettingsScreenMemberLabelsDialogPreview() {
  PermissionsSettingsScreen(
    state = PermissionsSettingsState(
      selfCanEditSettings = true,
      nonAdminCanSetMemberLabel = true,
      nonAdminsHaveMemberLabels = true,
      dialog = Dialog.MemberLabelsWillBeCleared
    ),
    onEvent = {},
    onNavigationClick = {}
  )
}
