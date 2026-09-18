/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.chatsettings.screens.grouppermissions

import androidx.annotation.StringRes
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
import org.signal.chatsettings.R
import org.signal.chatsettings.screens.grouppermissions.GroupPermissionsState.Dialog
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Rows.TextAndLabel
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.compose.Snackbars
import org.signal.core.ui.compose.showSnackbar

/** Row values, in the order [R.array.GroupPermissionsScreen__editor_labels] declares their labels. */
private const val VALUE_ONLY_ADMINS = "only_admins"
private const val VALUE_ALL_MEMBERS = "all_members"
private val EDITOR_VALUES = arrayOf(VALUE_ONLY_ADMINS, VALUE_ALL_MEMBERS)

/**
 * Lets a group admin choose which of the group's actions non-admins are allowed to take.
 */
@Composable
fun GroupPermissionsScreen(
  state: GroupPermissionsState,
  onEvent: (GroupPermissionsEvents) -> Unit,
  onNavigationClick: () -> Unit
) {
  val editorLabels = stringArrayResource(R.array.GroupPermissionsScreen__editor_labels)

  Scaffolds.Settings(
    title = stringResource(R.string.GroupPermissionsScreen__permissions),
    onNavigationClick = onNavigationClick,
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    navigationContentDescription = stringResource(R.string.GroupPermissionsScreen__go_back),
    snackbarHost = {
      ErrorSnackbarHost(
        errorMessage = state.errorMessage,
        onDismiss = { onEvent(GroupPermissionsEvents.SnackbarDismissed) }
      )
    }
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .padding(paddingValues)
        .testTag(GroupPermissionsTestTags.CONTENT)
    ) {
      item {
        PermissionRow(
          text = stringResource(R.string.GroupPermissionsScreen__add_members),
          dialogTitle = stringResource(R.string.GroupPermissionsScreen__who_can_add_new_members),
          labels = editorLabels,
          nonAdminAllowed = state.permissions.nonAdminCanAddMembers,
          onSelected = { onEvent(GroupPermissionsEvents.SetNonAdminCanAddMembers(it)) },
          modifier = Modifier.testTag(GroupPermissionsTestTags.ADD_MEMBERS_ROW),
          enabled = state.permissions.selfCanEditSettings
        )
      }

      item {
        PermissionRow(
          text = stringResource(R.string.GroupPermissionsScreen__edit_group_info),
          dialogTitle = stringResource(R.string.GroupPermissionsScreen__who_can_edit_this_groups_info),
          labels = editorLabels,
          nonAdminAllowed = state.permissions.nonAdminCanEditGroupInfo,
          onSelected = { onEvent(GroupPermissionsEvents.SetNonAdminCanEditGroupInfo(it)) },
          modifier = Modifier.testTag(GroupPermissionsTestTags.EDIT_GROUP_INFO_ROW),
          enabled = state.permissions.selfCanEditSettings
        )
      }

      item {
        PermissionRow(
          text = stringResource(R.string.GroupPermissionsScreen__send_messages),
          dialogTitle = stringResource(R.string.GroupPermissionsScreen__who_can_send_messages),
          labels = editorLabels,
          nonAdminAllowed = state.permissions.nonAdminCanSendMessages,
          onSelected = { onEvent(GroupPermissionsEvents.SetNonAdminCanSendMessages(it)) },
          modifier = Modifier.testTag(GroupPermissionsTestTags.SEND_MESSAGES_ROW),
          enabled = state.permissions.selfCanEditSettings
        )
      }

      item {
        PermissionRow(
          text = stringResource(R.string.GroupPermissionsScreen__add_member_labels),
          dialogTitle = stringResource(R.string.GroupPermissionsScreen__who_can_add_member_labels),
          labels = editorLabels,
          nonAdminAllowed = state.permissions.nonAdminCanSetMemberLabel,
          onSelected = { onEvent(GroupPermissionsEvents.SetNonAdminCanSetMemberLabel(it)) },
          modifier = Modifier.testTag(GroupPermissionsTestTags.ADD_MEMBER_LABELS_ROW),
          enabled = state.permissions.selfCanEditSettings
        )
      }
    }
  }

  GroupPermissionsDialogs(
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
private fun ErrorSnackbarHost(
  @StringRes errorMessage: Int?,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  val hostState = remember { SnackbarHostState() }
  val message = errorMessage?.let { stringResource(it) }

  LaunchedEffect(errorMessage) {
    if (message != null) {
      hostState.showSnackbar(message = message, duration = Snackbars.Duration.LONG)
      onDismiss()
    }
  }

  Snackbars.Host(hostState, modifier = modifier)
}

@Composable
private fun GroupPermissionsDialogs(
  state: GroupPermissionsState,
  onEvent: (GroupPermissionsEvents) -> Unit
) {
  when (state.dialog) {
    Dialog.MemberLabelsWillBeCleared -> Dialogs.SimpleAlertDialog(
      title = stringResource(R.string.GroupPermissionsScreen__member_labels_will_be_cleared_title),
      body = stringResource(R.string.GroupPermissionsScreen__member_labels_will_be_cleared_body),
      confirm = stringResource(R.string.GroupPermissionsScreen__change_permission),
      dismiss = stringResource(android.R.string.cancel),
      onConfirm = { onEvent(GroupPermissionsEvents.MemberLabelsWillBeClearedConfirmed) },
      onDismiss = { onEvent(GroupPermissionsEvents.DialogDismissed) }
    )

    Dialog.None -> Unit
  }
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun GroupPermissionsScreenPreview() {
  GroupPermissionsScreen(
    state = GroupPermissionsState(
      permissions = GroupPermissions.NONE.copy(
        selfCanEditSettings = true,
        nonAdminCanAddMembers = true,
        nonAdminCanSendMessages = true,
        nonAdminCanSetMemberLabel = true
      )
    ),
    onEvent = {},
    onNavigationClick = {}
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun GroupPermissionsScreenNonAdminPreview() {
  GroupPermissionsScreen(
    state = GroupPermissionsState(
      permissions = GroupPermissions.NONE.copy(
        selfCanEditSettings = false,
        nonAdminCanAddMembers = true,
        nonAdminCanSendMessages = true
      )
    ),
    onEvent = {},
    onNavigationClick = {}
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun GroupPermissionsScreenMemberLabelsDialogPreview() {
  GroupPermissionsScreen(
    state = GroupPermissionsState(
      permissions = GroupPermissions.NONE.copy(
        selfCanEditSettings = true,
        nonAdminCanSetMemberLabel = true,
        nonAdminsHaveMemberLabels = true
      ),
      dialog = Dialog.MemberLabelsWillBeCleared
    ),
    onEvent = {},
    onNavigationClick = {}
  )
}
