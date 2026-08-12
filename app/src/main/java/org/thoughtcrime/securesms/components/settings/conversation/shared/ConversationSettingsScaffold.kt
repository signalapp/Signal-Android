/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation.shared

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.emoji.EmojiText
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.AvatarImage
import org.thoughtcrime.securesms.recipients.Recipient
import org.signal.core.ui.R as CoreUiR

private val TOOLBAR_AVATAR_SIZE = 32.dp

/**
 * The frame that every conversation settings screen sits in, whoever the conversation is with: a settings toolbar
 * whose avatar and title fade in once the header has scrolled away, wrapped around a lazy list of setting rows.
 */
@Composable
fun ConversationSettingsScaffold(
  title: String,
  recipient: Recipient,
  onNavigationClick: () -> Unit,
  modifier: Modifier = Modifier,
  actions: @Composable RowScope.() -> Unit = {},
  content: LazyListScope.() -> Unit
) {
  val listState = rememberLazyListState()
  val showToolbarDetails by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
  val toolbarAlpha by animateFloatAsState(targetValue = if (showToolbarDetails) 1f else 0f, label = "toolbar-alpha")

  Scaffolds.Settings(
    title = title,
    onNavigationClick = onNavigationClick,
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    navigationContentDescription = stringResource(R.string.CallScreenTopBar__go_back),
    titleContent = { _, toolbarTitle ->
      ToolbarTitle(
        title = toolbarTitle,
        recipient = recipient,
        alpha = toolbarAlpha
      )
    },
    actions = actions,
    modifier = modifier
  ) { paddingValues ->
    LazyColumn(
      state = listState,
      modifier = Modifier.padding(paddingValues),
      content = content
    )
  }
}

/**
 * The scaffold at rest, which is how it looks before the list has been scrolled. The toolbar's own avatar and title are
 * faded out at this point, since the header below is already showing them -- see [ToolbarTitleScrolledPreview].
 */
@Composable
private fun ToolbarTitle(
  title: String,
  recipient: Recipient,
  alpha: Float,
  modifier: Modifier = Modifier
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier.alpha(alpha)
  ) {
    if (recipient != Recipient.UNKNOWN) {
      AvatarImage(
        recipient = recipient,
        useProfile = false,
        modifier = Modifier.size(TOOLBAR_AVATAR_SIZE)
      )

      Spacer(modifier = Modifier.width(12.dp))
    }

    EmojiText(
      text = title,
      style = MaterialTheme.typography.titleLarge,
      maxLines = 1
    )
  }
}

private val SCAFFOLD_PREVIEW_ROWS = listOf(
  "Disappearing messages",
  "Chat color & wallpaper",
  "Sounds & notifications",
  "Starred messages"
)

@DayNightPreviews
@Composable
private fun ConversationSettingsScaffoldPreview() {
  Previews.Preview {
    ConversationSettingsScaffold(
      title = "Deep Space Nine",
      recipient = previewRecipient(1L, groupName = "Deep Space Nine"),
      onNavigationClick = {},
      actions = {
        IconButton(onClick = {}) {
          Icon(
            painter = painterResource(CoreUiR.drawable.symbol_edit_24),
            contentDescription = null
          )
        }
      }
    ) {
      item {
        ConversationHeader(
          recipient = previewRecipient(1L, groupName = "Deep Space Nine"),
          name = "Deep Space Nine",
          subhead = "2 members"
        )
      }

      item {
        CallBar(
          state = CallBarState(isVideoAvailable = true, isMuteAvailable = true, isSearchAvailable = true),
          onAddToStoryClick = {},
          onMessageClick = {},
          onVideoCallClick = {},
          onAudioCallClick = {},
          onMuteClick = {},
          onMuteDurationSelected = {},
          onMuteUntilCustomTimeClick = {},
          onMuteMenuDismissed = {},
          onSearchClick = {}
        )
      }

      item { Dividers.Default() }

      items(SCAFFOLD_PREVIEW_ROWS) { row ->
        Rows.TextRow(text = row, onClick = {})
      }
    }
  }
}

/** What the toolbar looks like once the header has scrolled away and it has faded in. */
@DayNightPreviews
@Composable
private fun ToolbarTitleScrolledPreview() {
  Previews.Preview {
    ToolbarTitle(
      title = "Deep Space Nine",
      recipient = previewRecipient(1L, groupName = "Deep Space Nine"),
      alpha = 1f
    )
  }
}
