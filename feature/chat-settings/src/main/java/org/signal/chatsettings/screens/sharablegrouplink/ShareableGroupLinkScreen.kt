/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.chatsettings.screens.sharablegrouplink

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewWrapper
import org.signal.chatsettings.ErrorSnackbarHost
import org.signal.chatsettings.R
import org.signal.chatsettings.screens.sharablegrouplink.ShareableGroupLinkState.Dialog
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** A group change usually comes back quickly, so the spinner waits a beat before showing and then stays long enough not to flash. */
private val BUSY_DELAY = 300.milliseconds
private val BUSY_MINIMUM_DISPLAY = 1.seconds

/**
 * Lets a group's members share its link, and its admins turn that link on and off.
 */
@Composable
fun ShareableGroupLinkScreen(
  state: ShareableGroupLinkState,
  onEvent: (ShareableGroupLinkEvents) -> Unit,
  onShareClick: () -> Unit,
  onNavigationClick: () -> Unit
) {
  Scaffolds.Settings(
    title = stringResource(R.string.ShareableGroupLinkScreen__group_link),
    onNavigationClick = onNavigationClick,
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    navigationContentDescription = stringResource(R.string.ShareableGroupLinkScreen__go_back),
    snackbarHost = {
      ErrorSnackbarHost(
        errorMessage = state.errorMessage,
        onDismiss = { onEvent(ShareableGroupLinkEvents.SnackbarDismissed) }
      )
    }
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .padding(paddingValues)
        .testTag(ShareableGroupLinkTestTags.CONTENT)
    ) {
      item {
        Rows.ToggleRow(
          checked = state.groupLink.enabled,
          text = stringResource(R.string.ShareableGroupLinkScreen__group_link),
          label = state.groupLink.url.takeIf { state.groupLink.enabled },
          onCheckChanged = { onEvent(ShareableGroupLinkEvents.GroupLinkToggled) },
          modifier = Modifier.testTag(ShareableGroupLinkTestTags.GROUP_LINK_ROW),
          enabled = state.groupLink.selfCanEditSettings
        )
      }

      item {
        Rows.TextRow(
          text = stringResource(R.string.ShareableGroupLinkScreen__share),
          icon = SignalIcons.Share.painter,
          onClick = onShareClick,
          modifier = Modifier.testTag(ShareableGroupLinkTestTags.SHARE_ROW),
          enabled = state.groupLink.enabled
        )
      }

      item {
        Rows.TextRow(
          text = stringResource(R.string.ShareableGroupLinkScreen__reset_link),
          icon = SignalIcons.Refresh.painter,
          onClick = { onEvent(ShareableGroupLinkEvents.ResetLinkClicked) },
          modifier = Modifier.testTag(ShareableGroupLinkTestTags.RESET_LINK_ROW),
          enabled = state.groupLink.enabled && state.groupLink.selfCanEditSettings
        )
      }

      item {
        Dividers.Default()
      }

      item {
        Rows.ToggleRow(
          checked = state.groupLink.requiresAdminApproval,
          text = stringResource(R.string.ShareableGroupLinkScreen__require_admin_approval),
          label = stringResource(R.string.ShareableGroupLinkScreen__require_an_admin_to_approve_new_members_joining_via_the_group_link),
          onCheckChanged = { onEvent(ShareableGroupLinkEvents.AdminApprovalToggled) },
          modifier = Modifier.testTag(ShareableGroupLinkTestTags.ADMIN_APPROVAL_ROW),
          enabled = state.groupLink.enabled && state.groupLink.selfCanEditSettings
        )
      }
    }
  }

  ShareableGroupLinkDialogs(
    state = state,
    onEvent = onEvent
  )
}

@Composable
private fun ShareableGroupLinkDialogs(
  state: ShareableGroupLinkState,
  onEvent: (ShareableGroupLinkEvents) -> Unit
) {
  Dialogs.IndeterminateProgressDialog(
    visible = state.busy,
    delayDuration = BUSY_DELAY,
    minimumDisplayDuration = BUSY_MINIMUM_DISPLAY
  )

  when (state.dialog) {
    Dialog.ConfirmResetLink -> Dialogs.SimpleAlertDialog(
      title = Dialogs.NoTitle,
      body = stringResource(R.string.ShareableGroupLinkScreen__are_you_sure_you_want_to_reset_the_group_link),
      confirm = stringResource(R.string.ShareableGroupLinkScreen__reset_link),
      dismiss = stringResource(android.R.string.cancel),
      onConfirm = { onEvent(ShareableGroupLinkEvents.ResetLinkConfirmed) },
      onDismiss = { onEvent(ShareableGroupLinkEvents.DialogDismissed) }
    )

    Dialog.None -> Unit
  }
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun ShareableGroupLinkScreenPreview() {
  ShareableGroupLinkScreen(
    state = ShareableGroupLinkState(
      groupLink = GroupLink.NONE.copy(
        enabled = true,
        url = "https://signal.group/#CjQKIP_ZZ3Zz",
        requiresAdminApproval = true,
        selfCanEditSettings = true
      )
    ),
    onEvent = {},
    onShareClick = {},
    onNavigationClick = {}
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun ShareableGroupLinkScreenDisabledPreview() {
  ShareableGroupLinkScreen(
    state = ShareableGroupLinkState(groupLink = GroupLink.NONE.copy(selfCanEditSettings = true)),
    onEvent = {},
    onShareClick = {},
    onNavigationClick = {}
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun ShareableGroupLinkScreenNonAdminPreview() {
  ShareableGroupLinkScreen(
    state = ShareableGroupLinkState(
      groupLink = GroupLink.NONE.copy(
        enabled = true,
        url = "https://signal.group/#CjQKIP_ZZ3Zz",
        selfCanEditSettings = false
      )
    ),
    onEvent = {},
    onShareClick = {},
    onNavigationClick = {}
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun ShareableGroupLinkScreenResetLinkDialogPreview() {
  ShareableGroupLinkScreen(
    state = ShareableGroupLinkState(
      groupLink = GroupLink.NONE.copy(
        enabled = true,
        selfCanEditSettings = true
      ),
      dialog = Dialog.ConfirmResetLink
    ),
    onEvent = {},
    onShareClick = {},
    onNavigationClick = {}
  )
}
