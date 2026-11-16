/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.mutednotifications

import androidx.annotation.VisibleForTesting
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
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.R as CoreUiR

@VisibleForTesting
object MutedNotificationsTestTags {
  const val SCROLLER = "muted_notifications_scroller"
  const val ROW_CALLS = "muted_notifications_row_calls"
  const val ROW_MENTIONS = "muted_notifications_row_mentions"
  const val ROW_REPLIES = "muted_notifications_row_replies"
}

/**
 * Which notifications still come through while chats are muted, for every chat or for a single one.
 */
@Composable
fun MutedNotificationsScreen(
  state: MutedNotificationsState,
  onEvent: (MutedNotificationsEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  Scaffolds.Settings(
    title = stringResource(R.string.MutedNotificationsFragment__while),
    onNavigationClick = { onEvent(MutedNotificationsEvent.NavigateBackClicked) },
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    navigationContentDescription = stringResource(CoreUiR.string.CloseButton__content_description),
    modifier = modifier,
    expandableTitle = true
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .padding(paddingValues)
        .verticalScroll(rememberScrollState())
        .appSettingsScreen()
        .testTag(MutedNotificationsTestTags.SCROLLER)
    ) {
      Rows.ToggleRow(
        icon = SignalIcons.Phone.imageVector,
        checked = state.allowCalls,
        text = stringResource(R.string.MutedNotificationsScreen__calls),
        label = stringResource(if (state.isGlobal) R.string.MutedNotificationsFragment__calls_body_global else R.string.MutedNotificationsFragment__calls_body),
        onCheckChanged = { onEvent(MutedNotificationsEvent.CallsToggled(it)) },
        modifier = Modifier.testTag(MutedNotificationsTestTags.ROW_CALLS)
      )

      if (state.showMentions) {
        Rows.ToggleRow(
          icon = SignalIcons.At.imageVector,
          checked = state.allowMentions,
          text = stringResource(R.string.MutedNotificationsScreen__mentions),
          label = stringResource(if (state.isGlobal) R.string.MutedNotificationsFragment__mentions_body_global else R.string.MutedNotificationsFragment__mentions_body),
          onCheckChanged = { onEvent(MutedNotificationsEvent.MentionsToggled(it)) },
          modifier = Modifier.testTag(MutedNotificationsTestTags.ROW_MENTIONS)
        )
      }

      if (state.showReplies) {
        Rows.ToggleRow(
          icon = SignalIcons.Reply.imageVector,
          checked = state.allowReplies,
          text = stringResource(R.string.MutedNotificationsScreen__replies),
          label = stringResource(if (state.isGlobal) R.string.MutedNotificationsFragment__replies_body_global else R.string.MutedNotificationsFragment__replies_body),
          onCheckChanged = { onEvent(MutedNotificationsEvent.RepliesToggled(it)) },
          modifier = Modifier.testTag(MutedNotificationsTestTags.ROW_REPLIES)
        )
      }
    }
  }
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun MutedNotificationsScreenGlobalPreview() {
  MutedNotificationsScreen(
    state = MutedNotificationsState(isGlobal = true, allowCalls = true, allowMentions = false, allowReplies = true),
    onEvent = {}
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun MutedNotificationsScreenGroupPreview() {
  MutedNotificationsScreen(
    state = MutedNotificationsState(isGlobal = false, allowCalls = true, allowMentions = false, allowReplies = true),
    onEvent = {}
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun MutedNotificationsScreenOneToOnePreview() {
  MutedNotificationsScreen(
    state = MutedNotificationsState(isGlobal = false, allowCalls = true, showMentions = false, showReplies = false),
    onEvent = {}
  )
}
