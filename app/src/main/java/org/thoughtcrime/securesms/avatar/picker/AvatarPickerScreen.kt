/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.avatar.picker

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import kotlinx.coroutines.flow.first
import org.signal.core.ui.WindowBreakpoint
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.DropdownMenus
import org.signal.core.ui.compose.IconButtons
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.compose.Snackbars
import org.signal.core.ui.compose.horizontalGutters
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.ui.rememberWindowBreakpoint
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.Avatar
import org.thoughtcrime.securesms.avatar.compose.AvatarImage
import org.signal.core.ui.R as CoreUiR

private val AVATAR_SIZE = 72.dp
private val SELECTED_AVATAR_SIZE = 56.dp
private val AVATAR_SPACING = 24.dp
private val AVATAR_GRID_INSET_LARGE = 112.dp

private const val HEADER_ITEM_COUNT = 1

@Composable
fun AvatarPickerScreen(
  state: AvatarPickerState,
  onEvent: (AvatarPickerEvents) -> Unit,
  snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
  Scaffolds.Settings(
    title = "",
    onNavigationClick = { onEvent(AvatarPickerEvents.Close) },
    navigationIcon = SignalIcons.X.imageVector,
    snackbarHost = { Snackbars.Host(snackbarHostState) },
    bottomBar = {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .navigationBarsPadding()
          .padding(end = 24.dp, bottom = 24.dp),
        contentAlignment = Alignment.CenterEnd
      ) {
        Buttons.LargePrimary(
          onClick = { onEvent(AvatarPickerEvents.Save) },
          enabled = state.canSave,
          modifier = Modifier.testTag(AvatarPickerTestTags.SAVE_BUTTON)
        ) {
          Text(text = stringResource(R.string.AvatarPickerFragment__save))
        }
      }
    }
  ) { contentPadding ->
    val gridInset = when (rememberWindowBreakpoint()) {
      is WindowBreakpoint.Small -> 0.dp
      else -> AVATAR_GRID_INSET_LARGE
    }

    val gridState = rememberLazyGridState()
    val avatarHeight = with(LocalDensity.current) { AVATAR_SIZE.roundToPx() }
    val selectedIndex = state.selectableAvatars.indexOfFirst { it == state.currentAvatar }

    LaunchedEffect(selectedIndex) {
      gridState.animateItemIntoView(
        index = if (selectedIndex >= 0) selectedIndex + HEADER_ITEM_COUNT else 0,
        itemHeight = avatarHeight
      )
    }

    BoxWithConstraints(modifier = Modifier.padding(contentPadding)) {
      val viewportWidth = maxWidth

      LazyVerticalGrid(
        state = gridState,
        columns = GridCells.FixedSize(AVATAR_SIZE),
        horizontalArrangement = spacedBy(AVATAR_SPACING, Alignment.CenterHorizontally),
        verticalArrangement = spacedBy(AVATAR_SPACING),
        contentPadding = PaddingValues(
          start = dimensionResource(CoreUiR.dimen.gutter) + gridInset,
          end = dimensionResource(CoreUiR.dimen.gutter) + gridInset,
          bottom = AVATAR_SPACING
        ),
        modifier = Modifier.testTag(AvatarPickerTestTags.AVATAR_GRID)
      ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
          AvatarPickerHeader(
            state = state,
            onEvent = onEvent,
            // A full span item is only as wide as the cell run, so measure against the viewport instead.
            modifier = Modifier
              .wrapContentWidth(unbounded = true)
              .width(viewportWidth)
          )
        }

        items(
          items = state.selectableAvatars,
          key = { it.gridKey }
        ) { avatar ->
          SelectableAvatar(
            avatar = avatar,
            isSelected = state.currentAvatar == avatar,
            onEvent = onEvent,
            modifier = Modifier.testTag(AvatarPickerTestTags.SELECTABLE_AVATAR)
          )
        }
      }
    }
  }
}

@Composable
private fun AvatarPickerHeader(
  state: AvatarPickerState,
  onEvent: (AvatarPickerEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  Column(modifier = modifier) {
    CurrentAvatar(
      avatar = state.currentAvatar,
      canClear = state.canClear,
      onClearClick = { onEvent(AvatarPickerEvents.ClearAvatar) },
      modifier = Modifier
        .align(Alignment.CenterHorizontally)
        .size(dimensionResource(R.dimen.avatar_picker_image_width))
    )

    Row(
      horizontalArrangement = spacedBy(32.dp),
      modifier = Modifier
        .align(Alignment.CenterHorizontally)
        .padding(top = 32.dp, bottom = 28.dp)
        .horizontalGutters()
    ) {
      Buttons.ActionButton(
        imageVector = SignalIcons.Camera.imageVector,
        label = stringResource(R.string.AvatarPickerFragment__camera),
        onClick = { onEvent(AvatarPickerEvents.CapturePhoto) },
        modifier = Modifier.testTag(AvatarPickerTestTags.CAMERA_BUTTON)
      )

      Buttons.ActionButton(
        imageVector = SignalIcons.Photo.imageVector,
        label = stringResource(R.string.AvatarPickerFragment__photo),
        onClick = { onEvent(AvatarPickerEvents.SelectPhoto) },
        modifier = Modifier.testTag(AvatarPickerTestTags.PHOTO_BUTTON)
      )

      Buttons.ActionButton(
        imageVector = SignalIcons.Text.imageVector,
        label = stringResource(R.string.AvatarPickerFragment__text),
        onClick = { onEvent(AvatarPickerEvents.SelectText) },
        modifier = Modifier.testTag(AvatarPickerTestTags.TEXT_BUTTON)
      )
    }

    Dividers.Default()
  }
}

@Composable
private fun CurrentAvatar(
  avatar: Avatar?,
  canClear: Boolean,
  onClearClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Crossfade(
    targetState = avatar,
    modifier = modifier
  ) { targetState ->
    if (targetState == null) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(color = MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape)
      )
    } else {
      Box(modifier = Modifier.fillMaxSize()) {
        AvatarImage(
          avatar = targetState,
          contentDescription = stringResource(R.string.AvatarPickerFragment__avatar_preview),
          modifier = Modifier
            .fillMaxSize()
            .clip(CircleShape)
        )

        if (canClear) {
          IconButtons.IconButton(
            onClick = onClearClick,
            modifier = Modifier
              .testTag(AvatarPickerTestTags.CLEAR_AVATAR_BUTTON)
              .padding(4.dp)
              .background(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = CircleShape
              )
              .align(Alignment.TopEnd)
          ) {
            Icon(
              imageVector = SignalIcons.X.imageVector,
              contentDescription = stringResource(R.string.AvatarPickerFragment__clear_avatar)
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SelectableAvatar(
  avatar: Avatar,
  isSelected: Boolean,
  onEvent: (AvatarPickerEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  val haptics = LocalHapticFeedback.current
  val menuController = remember { DropdownMenus.MenuController() }
  val selectedProgress by animateFloatAsState(targetValue = if (isSelected) 1f else 0f, label = "avatar-selection")
  val avatarSize = lerp(AVATAR_SIZE, SELECTED_AVATAR_SIZE, selectedProgress)

  Box(
    contentAlignment = Alignment.Center,
    modifier = modifier
      .size(AVATAR_SIZE)
      .border(
        width = 3.dp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = selectedProgress),
        shape = CircleShape
      )
      .clip(CircleShape)
      .combinedClickable(
        role = Role.Button,
        onClick = {
          if (!isSelected) {
            onEvent(AvatarPickerEvents.AvatarSelected(avatar))
          } else {
            onEvent(AvatarPickerEvents.EditAvatar(avatar))
          }
        },
        onLongClick = if (avatar.canDelete) {
          {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            menuController.show()
          }
        } else {
          null
        },
        onLongClickLabel = stringResource(R.string.AvatarPickerFragment__avatar_options)
      )
  ) {
    AvatarImage(
      avatar = avatar,
      contentDescription = null,
      modifier = Modifier
        .size(avatarSize)
        .clip(CircleShape)
    )

    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier
        .alpha(selectedProgress)
        .background(color = Color.Black.copy(alpha = 0.2f), shape = CircleShape)
        .size(avatarSize)
    ) {
      Icon(
        imageVector = SignalIcons.Edit.imageVector,
        contentDescription = stringResource(R.string.AvatarPickerFragment__edit_avatar),
        tint = SignalTheme.colors.colorOnCustom
      )
    }

    DropdownMenus.Menu(
      controller = menuController,
      offsetX = 0.dp,
      offsetY = 4.dp
    ) { controller ->
      DropdownMenus.Item(
        text = { Text(text = stringResource(R.string.delete)) },
        onClick = {
          controller.hide()
          onEvent(AvatarPickerEvents.DeleteAvatar(avatar))
        }
      )
    }
  }
}

/** Scrolls [index] into view with the smallest scroll possible. Items already fully on screen are left alone. */
private suspend fun LazyGridState.animateItemIntoView(index: Int, itemHeight: Int) {
  snapshotFlow { layoutInfo.visibleItemsInfo }.first { it.isNotEmpty() }

  val viewportHeight = layoutInfo.viewportSize.height
  val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }

  if (item != null && item.offset.y >= 0 && item.offset.y + item.size.height <= viewportHeight) {
    return
  }

  if (index > firstVisibleItemIndex) {
    animateScrollToItem(index, scrollOffset = -(viewportHeight - itemHeight))
  } else {
    animateScrollToItem(index)
  }
}

/** Stable identity so item state follows the avatar rather than its position. */
private val Avatar.gridKey: String
  get() {
    val id = (databaseId as? Avatar.DatabaseId.Saved)?.id
    return when (this) {
      is Avatar.Photo -> "photo:$id:$uri"
      is Avatar.Text -> "text:$id:$text"
      is Avatar.Vector -> "vector:$id:$key"
      is Avatar.Resource -> "resource:$resourceId"
    }
  }

/** Only persisted avatars the user created can be deleted. */
private val Avatar.canDelete: Boolean
  get() = databaseId is Avatar.DatabaseId.Saved && (this is Avatar.Photo || this is Avatar.Text)

@PreviewWrapper(SignalPreviewWrapper::class)
@AllDevicePreviews
@Composable
private fun AvatarPickerScreenPreview() {
  val avatars = remember { AvatarPickerRepository.getDefaultAvatarsForSelf() }

  AvatarPickerScreen(
    state = remember {
      AvatarPickerState(
        currentAvatar = avatars.first(),
        selectableAvatars = avatars,
        canSave = true,
        canClear = true
      )
    },
    onEvent = {}
  )
}
