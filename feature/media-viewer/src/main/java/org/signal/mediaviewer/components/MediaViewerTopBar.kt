/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediaviewer.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.DropdownMenus
import org.signal.core.ui.compose.Previews
import org.signal.mediaviewer.MediaViewerEvent
import org.signal.mediaviewer.MediaViewerItem
import org.signal.mediaviewer.MediaViewerPreviewFixtures
import org.signal.mediaviewer.MediaViewerScreenTestTags
import org.signal.mediaviewer.R
import org.signal.mediaviewer.playback.MediaViewerPlaybackController
import org.signal.core.ui.R as CoreUiR

/**
 * Toolbar: back button, sender/time labels that jump to the message's chat, and the forward, share,
 * and overflow menu actions.
 */
@Composable
internal fun MediaViewerTopBar(
  item: MediaViewerItem?,
  isDraft: Boolean,
  showThread: Boolean,
  playbackController: MediaViewerPlaybackController,
  onEvent: (MediaViewerEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
      .fillMaxWidth()
      .height(64.dp)
      .testTag(MediaViewerScreenTestTags.TOP_BAR)
  ) {
    IconButton(
      onClick = { onEvent(MediaViewerEvent.BackClicked) },
      modifier = Modifier.testTag(MediaViewerScreenTestTags.BACK_BUTTON)
    ) {
      Icon(
        painter = painterResource(CoreUiR.drawable.symbol_arrow_start_24),
        contentDescription = stringResource(R.string.MediaViewerScreen__navigate_back),
        tint = MaterialTheme.colorScheme.onSurface
      )
    }

    TitleArea(
      item = item,
      isDraft = isDraft,
      showThread = showThread,
      onEvent = onEvent,
      modifier = Modifier
        .weight(1f)
        .padding(horizontal = 4.dp)
    )

    IconButton(
      onClick = {
        playbackController.pause()
        onEvent(MediaViewerEvent.ForwardClicked)
      },
      modifier = Modifier.testTag(MediaViewerScreenTestTags.FORWARD_BUTTON)
    ) {
      Icon(
        painter = painterResource(CoreUiR.drawable.symbol_forward_24),
        contentDescription = stringResource(R.string.MediaViewerScreen__forward),
        tint = MaterialTheme.colorScheme.onSurface
      )
    }

    IconButton(
      onClick = {
        playbackController.pause()
        onEvent(MediaViewerEvent.ShareClicked)
      },
      modifier = Modifier.testTag(MediaViewerScreenTestTags.SHARE_BUTTON)
    ) {
      Icon(
        painter = painterResource(CoreUiR.drawable.symbol_share_android_24),
        contentDescription = stringResource(R.string.MediaViewerScreen__share),
        tint = MaterialTheme.colorScheme.onSurface
      )
    }

    OverflowMenu(
      isDraft = isDraft,
      onEvent = onEvent
    )
  }
}

@Composable
private fun TitleArea(
  item: MediaViewerItem?,
  isDraft: Boolean,
  showThread: Boolean,
  onEvent: (MediaViewerEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  val canJumpToMessage = !isDraft && item != null && item.messageId > 0
  val title = item?.let { titleText(it, showThread) }.orEmpty()
  val datedItem = item?.takeIf { !isDraft && it.date > 0 }
  val draft = stringResource(R.string.MediaViewerScreen__draft)
  val subtitle = datedItem?.formattedDate ?: draft
  val subtitleContentDescription = datedItem?.formattedDateContentDescription ?: draft

  Column(
    modifier = modifier
      .testTag(MediaViewerScreenTestTags.TOOLBAR_TITLE)
      .semantics(mergeDescendants = true) { contentDescription = "$title $subtitleContentDescription" }
      .then(
        if (canJumpToMessage) {
          Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
          ) { onEvent(MediaViewerEvent.ToolbarTitleClicked) }
        } else {
          Modifier
        }
      )
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal, fontSize = 18.sp, lineHeight = 28.sp),
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )

      if (canJumpToMessage) {
        Icon(
          painter = painterResource(CoreUiR.drawable.symbol_chevron_right_24),
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.size(12.dp)
        )
      }
    }
  }
}

@Composable
private fun titleText(item: MediaViewerItem, showThread: Boolean): String {
  val fromName = if (item.isOutgoing) stringResource(R.string.MediaViewerScreen__you) else item.fromDisplayName
  if (!showThread) {
    return fromName
  }

  return when {
    item.isNoteToSelf -> stringResource(R.string.MediaViewerScreen__note_to_self)
    item.isOutgoing -> stringResource(R.string.MediaViewerScreen__you_to_s, item.threadDisplayName)
    item.isThreadGroup -> stringResource(R.string.MediaViewerScreen__s_to_s, fromName, item.threadDisplayName)
    else -> stringResource(R.string.MediaViewerScreen__s_to_you, fromName)
  }
}

@Composable
private fun OverflowMenu(
  isDraft: Boolean,
  onEvent: (MediaViewerEvent) -> Unit
) {
  Box {
    val controller = remember { DropdownMenus.MenuController() }

    IconButton(
      onClick = { controller.show() },
      modifier = Modifier.testTag(MediaViewerScreenTestTags.OVERFLOW_BUTTON)
    ) {
      Icon(
        painter = painterResource(CoreUiR.drawable.symbol_more_vertical_24),
        contentDescription = stringResource(R.string.MediaViewerScreen__more_options),
        tint = MaterialTheme.colorScheme.onSurface
      )
    }

    DropdownMenus.Menu(controller = controller) {
      DropdownMenus.Item(
        leadingIconResId = CoreUiR.drawable.symbol_edit_24,
        text = { Text(text = stringResource(R.string.MediaViewerScreen__edit)) },
        onClick = {
          controller.hide()
          onEvent(MediaViewerEvent.EditClicked)
        },
        modifier = Modifier.testTag(MediaViewerScreenTestTags.MENU_EDIT)
      )

      DropdownMenus.Item(
        leadingIconResId = CoreUiR.drawable.symbol_save_android_24,
        text = { Text(text = stringResource(R.string.MediaViewerScreen__save)) },
        onClick = {
          controller.hide()
          onEvent(MediaViewerEvent.SaveClicked)
        },
        modifier = Modifier.testTag(MediaViewerScreenTestTags.MENU_SAVE)
      )

      if (!isDraft) {
        DropdownMenus.Item(
          leadingIconResId = CoreUiR.drawable.symbol_trash_24,
          text = { Text(text = stringResource(R.string.MediaViewerScreen__delete)) },
          onClick = {
            controller.hide()
            onEvent(MediaViewerEvent.DeleteClicked)
          },
          modifier = Modifier.testTag(MediaViewerScreenTestTags.MENU_DELETE)
        )
      }
    }
  }
}

@DayNightPreviews
@Composable
private fun MediaViewerTopBarPreview() {
  Previews.Preview {
    MediaViewerTopBar(
      item = MediaViewerPreviewFixtures.previewItem(),
      isDraft = false,
      showThread = false,
      playbackController = MediaViewerPlaybackController(),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun MediaViewerTopBarThreadPreview() {
  Previews.Preview {
    MediaViewerTopBar(
      item = MediaViewerPreviewFixtures.previewItem(isOutgoing = true),
      isDraft = false,
      showThread = true,
      playbackController = MediaViewerPlaybackController(),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun MediaViewerTopBarDraftPreview() {
  Previews.Preview {
    MediaViewerTopBar(
      item = MediaViewerPreviewFixtures.previewItem(),
      isDraft = true,
      showThread = false,
      playbackController = MediaViewerPlaybackController(),
      onEvent = {}
    )
  }
}
