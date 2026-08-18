package org.thoughtcrime.securesms.components.settings.app.notifications

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Rows
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.RemoteConfig

@Composable
fun MutedNotificationScreen(
  state: MutedNotificationsState,
  onEvent: (MutedNotificationsEvent) -> Unit = {},
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
  ) {
    if (RemoteConfig.internalUser) {
      Rows.ToggleRow(
        icon = ImageVector.vectorResource(org.signal.core.ui.R.drawable.symbol_phone_24),
        checked = state.allowCalls,
        text = stringResource(R.string.MutedNotificationsFragment__calls),
        label = if (state.isGlobal) stringResource(R.string.MutedNotificationsFragment__calls_body_global) else stringResource(R.string.MutedNotificationsFragment__calls_body),
        onCheckChanged = { onEvent(MutedNotificationsEvent.CallsToggled(it)) }
      )
    }
    if (state.showMentions) {
      Rows.ToggleRow(
        icon = ImageVector.vectorResource(org.signal.core.ui.R.drawable.symbol_at_24),
        checked = state.allowMentions,
        text = stringResource(R.string.MutedNotificationsFragment__mentions),
        label = if (state.isGlobal) stringResource(R.string.MutedNotificationsFragment__mentions_body_global) else stringResource(R.string.MutedNotificationsFragment__mentions_body),
        onCheckChanged = { onEvent(MutedNotificationsEvent.MentionsToggled(it)) }
      )
    }
    if (RemoteConfig.internalUser) {
      if (state.showReplies) {
        Rows.ToggleRow(
          icon = ImageVector.vectorResource(R.drawable.symbol_reply_24),
          checked = state.allowReplies,
          text = stringResource(R.string.MutedNotificationsFragment__replies),
          label = if (state.isGlobal) stringResource(R.string.MutedNotificationsFragment__replies_body_global) else stringResource(R.string.MutedNotificationsFragment__replies_body),
          onCheckChanged = { onEvent(MutedNotificationsEvent.RepliesToggled(it)) }
        )
      }
    }
  }
}

@DayNightPreviews
@Composable
fun MutedNotificationScreenGlobalPreview() {
  Previews.Preview {
    MutedNotificationScreen(
      state = MutedNotificationsState(
        isGlobal = true,
        allowCalls = true,
        allowMentions = false,
        allowReplies = true
      )
    )
  }
}

@DayNightPreviews
@Composable
fun MutedNotificationScreenChatPreview() {
  Previews.Preview {
    MutedNotificationScreen(
      state = MutedNotificationsState(
        isGlobal = false,
        allowCalls = true,
        allowMentions = false,
        allowReplies = true
      )
    )
  }
}
