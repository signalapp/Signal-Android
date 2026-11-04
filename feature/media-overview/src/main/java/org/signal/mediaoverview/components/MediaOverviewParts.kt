/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediaoverview.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DropdownMenus
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.mediaoverview.MediaOverviewEvent
import org.signal.mediaoverview.MediaOverviewItem
import org.signal.mediaoverview.MediaOverviewTestTags
import org.signal.mediaoverview.R
import org.signal.core.ui.R as CoreUiR

/**
 * The play button over a video thumbnail, the error icon over a thumbnail that failed for good, and
 * the internal archive state hint.
 */
@Composable
internal fun MediaThumbnailOverlays(
  item: MediaOverviewItem,
  playOverlaySize: Dp,
  modifier: Modifier = Modifier
) {
  Box(modifier = modifier) {
    if (item.transferState == MediaOverviewItem.TransferState.FAILED) {
      Icon(
        imageVector = SignalIcons.ErrorCircle.imageVector,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.align(Alignment.Center)
      )
    } else if (item.showPlayOverlay) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .align(Alignment.Center)
          .size(playOverlaySize)
          .background(SignalTheme.colors.colorTransparentInverse4, CircleShape)
      ) {
        Icon(
          painter = painterResource(R.drawable.triangle_right),
          contentDescription = stringResource(R.string.MediaOverviewScreen__play_video),
          tint = SignalTheme.colors.colorOnCustom,
          modifier = Modifier.padding(playOverlaySize / 4)
        )
      }
    }

    ArchiveHintDot(hint = item.archiveHint, modifier = Modifier.align(Alignment.TopStart))
  }
}

@Composable
internal fun ArchiveHintDot(hint: MediaOverviewItem.ArchiveHint?, modifier: Modifier = Modifier) {
  if (hint == null) {
    return
  }

  val color = when (hint) {
    MediaOverviewItem.ArchiveHint.NONE -> {
      Color.Black
    }
    MediaOverviewItem.ArchiveHint.COPY_PENDING -> {
      Color.Blue
    }
    MediaOverviewItem.ArchiveHint.UPLOAD_IN_PROGRESS -> {
      Color.Cyan
    }
    MediaOverviewItem.ArchiveHint.FINISHED -> {
      Color.Green
    }
    MediaOverviewItem.ArchiveHint.TEMPORARY_FAILURE -> {
      Color.Yellow
    }
    MediaOverviewItem.ArchiveHint.PERMANENT_FAILURE -> {
      Color.Red
    }
  }

  Box(
    modifier = modifier
      .size(16.dp)
      .background(color, CircleShape)
  )
}

/**
 * The check mark shown on a selected item.
 */
@Composable
internal fun SelectedCheck(modifier: Modifier = Modifier) {
  Image(
    painter = painterResource(R.drawable.ic_check_circle_solid_20),
    contentDescription = stringResource(R.string.MediaOverviewScreen__selected),
    modifier = modifier
      .background(MaterialTheme.colorScheme.background, CircleShape)
      .padding(1.dp)
  )
}

/**
 * The menu shown when long-pressing [item]. Anchored to whatever box it is placed in.
 */
@Composable
internal fun MediaContextMenu(
  item: MediaOverviewItem,
  onEvent: (MediaOverviewEvent) -> Unit
) {
  DropdownMenus.Menu(
    expanded = true,
    onDismissRequest = { onEvent(MediaOverviewEvent.ContextMenuDismissed) },
    offsetX = 0.dp,
    offsetY = 4.dp
  ) {
    if (item.canForward) {
      ContextMenuItem(
        icon = CoreUiR.drawable.symbol_forward_24,
        label = R.string.MediaOverviewScreen__forward,
        testTag = MediaOverviewTestTags.CONTEXT_MENU_FORWARD,
        onClick = { onEvent(MediaOverviewEvent.ContextMenuForwardClicked) }
      )
    }

    if (item.canShare) {
      ContextMenuItem(
        icon = CoreUiR.drawable.symbol_share_android_24,
        label = R.string.MediaOverviewScreen__share,
        testTag = MediaOverviewTestTags.CONTEXT_MENU_SHARE,
        onClick = { onEvent(MediaOverviewEvent.ContextMenuShareClicked) }
      )
    }

    if (item.canSave) {
      ContextMenuItem(
        icon = CoreUiR.drawable.symbol_save_android_24,
        label = R.string.MediaOverviewScreen__save,
        testTag = MediaOverviewTestTags.CONTEXT_MENU_SAVE,
        onClick = { onEvent(MediaOverviewEvent.ContextMenuSaveClicked) }
      )
    }

    ContextMenuItem(
      icon = CoreUiR.drawable.symbol_trash_24,
      label = R.string.MediaOverviewScreen__delete,
      testTag = MediaOverviewTestTags.CONTEXT_MENU_DELETE,
      onClick = { onEvent(MediaOverviewEvent.ContextMenuDeleteClicked) }
    )

    ContextMenuItem(
      icon = CoreUiR.drawable.symbol_check_circle_24,
      label = R.string.MediaOverviewScreen__select,
      testTag = MediaOverviewTestTags.CONTEXT_MENU_SELECT,
      onClick = { onEvent(MediaOverviewEvent.ContextMenuSelectClicked) }
    )

    ContextMenuItem(
      icon = CoreUiR.drawable.symbol_open_24,
      label = R.string.MediaOverviewScreen__jump_to_message,
      testTag = MediaOverviewTestTags.CONTEXT_MENU_JUMP_TO_MESSAGE,
      onClick = { onEvent(MediaOverviewEvent.ContextMenuJumpToMessageClicked) }
    )
  }
}

@Composable
private fun ContextMenuItem(
  @DrawableRes icon: Int,
  @StringRes label: Int,
  testTag: String,
  onClick: () -> Unit
) {
  DropdownMenus.Item(
    leadingIconResId = icon,
    leadingIconSpacing = 4.dp,
    text = { Text(text = stringResource(label)) },
    onClick = onClick,
    modifier = Modifier.testTag(testTag)
  )
}
