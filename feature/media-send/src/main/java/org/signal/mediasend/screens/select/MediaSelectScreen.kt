/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.select

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.window.core.layout.WindowSizeClass
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import org.signal.core.models.media.Media
import org.signal.core.models.media.MediaFolder
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.DropdownMenus
import org.signal.core.ui.compose.LocalChatColorProvider
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.ensureWidthIsAtLeastHeight
import org.signal.core.ui.compose.list.DragSelectEvent
import org.signal.core.ui.compose.list.DragToSelectState
import org.signal.core.ui.compose.list.ReorderableItem
import org.signal.core.ui.compose.list.dragToSelect
import org.signal.core.ui.compose.list.rememberDragToSelectState
import org.signal.core.ui.compose.list.rememberReorderBuffer
import org.signal.core.ui.compose.list.rememberReorderableListState
import org.signal.core.ui.compose.list.reorderableList
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.util.ContentTypeUtil
import org.signal.glide.compose.GlideImage
import org.signal.mediasend.R
import org.signal.mediasend.screens.MediaSendMetrics
import org.signal.mediasend.screens.edit.rememberPreviewMedia
import org.signal.mediasend.test.TestTags
import org.signal.mediasend.util.formatAsClock
import kotlin.time.Duration.Companion.milliseconds
import org.signal.core.ui.permissions.Permissions as PermissionsUtil

/** How many empty tiles stand in for the gallery we are not allowed to show. Matches the v2 gallery. */
private const val PLACEHOLDER_COUNT = 100

/**
 * Allows user to select one or more pieces of content to add to the
 * current media selection.
 */
@Composable
internal fun MediaSelectScreen(
  state: MediaSelectState,
  onEvent: (MediaSelectScreenEvents) -> Unit,
  selectionAdditions: Flow<Media> = emptyFlow()
) {
  // Without read access there is nothing to browse, and with selected-photos access and nothing selected there is
  // nothing yet. Both show the placeholder grid behind a call to action, so both use the denser file grid.
  val showPlaceholders = state.mediaPermissions == MediaPermissions.NONE ||
    (state.mediaPermissions == MediaPermissions.PARTIAL && !state.hasContent)

  val gridConfiguration = rememberGridConfiguration(state is MediaSelectState.Folders && !showPlaceholders)
  val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
  val recipientChatColor: Color? = state.recipientId?.let { LocalChatColorProvider.current(it.id).value }

  val gridState = rememberLazyGridState()
  val dragToSelectState = rememberDragToSelectMediaState(state, onEvent, gridState)

  // Only an empty selection can leave an editor with nothing to edit behind us. Every other back press is left to the
  // navigation default, which keeps its predictive-back gesture.
  BackHandler(enabled = state.selectedMedia.isEmpty()) {
    onEvent(MediaSelectScreenEvents.NavigateBack)
  }

  // Once the selection starts refusing items there is nothing left for the drag to do, and letting it run on would
  // raise a refusal for every further tile it crosses.
  LaunchedEffect(state.isSelectionRejected) {
    if (state.isSelectionRejected) {
      dragToSelectState.cancel()
      onEvent(MediaSelectScreenEvents.SelectionRejectionShown)
    }
  }

  // The system prompt and the app settings round-trip both land us back here, and neither tells us what changed.
  val currentOnEvent by rememberUpdatedState(onEvent)
  LifecycleResumeEffect(Unit) {
    currentOnEvent(MediaSelectScreenEvents.Refresh)
    onPauseOrDispose { }
  }

  Scaffolds.Settings(
    title = when (state) {
      is MediaSelectState.Folders -> stringResource(R.string.MediaSelectScreen__gallery)
      is MediaSelectState.Files -> state.selectedMediaFolder.title
    },
    navigationIcon = ImageVector.vectorResource(org.signal.core.ui.R.drawable.symbol_arrow_start_24),
    onNavigationClick = { backDispatcher?.onBackPressed() },
    actions = {
      IconButton(onClick = {
        onEvent(MediaSelectScreenEvents.NavigateToCamera)
      }) {
        Icon(imageVector = SignalIcons.Camera.imageVector, contentDescription = stringResource(R.string.MediaSelectScreen__go_to_camera))
      }
    }
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .padding(paddingValues)
        .fillMaxSize()
    ) {
      // Selected-photos access that has something to show gets a persistent reminder rather than an interstitial,
      // so the user can keep browsing what they already shared while still having a way to share more.
      if (state.mediaPermissions == MediaPermissions.PARTIAL && state.hasContent) {
        LimitedAccessBar(onEvent)
      }

      Box(modifier = Modifier.weight(1f)) {
        LazyVerticalGrid(
          state = gridState,
          columns = gridConfiguration.gridCells,
          horizontalArrangement = spacedBy(gridConfiguration.horizontalSpacing),
          verticalArrangement = spacedBy(gridConfiguration.verticalSpacing),
          userScrollEnabled = !showPlaceholders && !dragToSelectState.isActive,
          modifier = Modifier
            .padding(horizontal = gridConfiguration.horizontalMargin)
            .fillMaxSize()
            .testTag(TestTags.MEDIA_SELECT_GRID)
            .then(
              if (state is MediaSelectState.Files && !showPlaceholders) {
                Modifier.dragToSelect(dragToSelectState)
              } else {
                Modifier
              }
            )
        ) {
          if (showPlaceholders) {
            items(PLACEHOLDER_COUNT) {
              MediaTilePlaceholder()
            }
          } else {
            when (state) {
              is MediaSelectState.Folders -> {
                items(state.mediaFolders, key = { it.bucketId }) {
                  MediaFolderTile(it, onEvent)
                }
              }

              is MediaSelectState.Files -> {
                items(state.selectedMediaFolderItems, key = { it.uri }) { media ->
                  MediaTile(
                    media = media,
                    selectionIndex = state.selectedMedia.indexOfFirst { it.uri == media.uri },
                    recipientChatColor = recipientChatColor,
                    onEvent = onEvent
                  )
                }
              }
            }
          }
        }

        if (showPlaceholders) {
          MediaAccessCallToAction(
            mediaPermissions = state.mediaPermissions,
            onEvent = onEvent,
            modifier = Modifier.align(Alignment.Center)
          )
        }
      }

      AnimatedVisibility(
        visible = state.selectedMedia.isNotEmpty(),
        enter = expandVertically(
          expandFrom = Alignment.Top,
          animationSpec = spring()
        ) + fadeIn(animationSpec = spring()),
        exit = shrinkVertically(
          shrinkTowards = Alignment.Top,
          animationSpec = spring()
        ) + fadeOut(animationSpec = spring()),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier
            .background(color = MaterialTheme.colorScheme.surface)
            .padding(vertical = gridConfiguration.bottomBarVerticalPadding, horizontal = gridConfiguration.bottomBarHorizontalPadding)
        ) {
          SelectedMediaRow(
            selectedMedia = state.selectedMedia,
            selectionAdditions = selectionAdditions,
            alignment = gridConfiguration.bottomBarAlignment,
            onEvent = onEvent,
            modifier = Modifier
              .weight(1f)
              .padding(end = 16.dp)
          )

          NextButton(
            mediaSelectionCount = state.selectedMedia.size,
            recipientChatColor = recipientChatColor
          ) {
            onEvent(MediaSelectScreenEvents.NavigateToEdit)
          }
        }
      }
    }
  }
}

/**
 * Turns the covered index ranges reported by the gesture into batched selection events for the media those indices
 * stand for. Only the file grid is homogeneous enough for this: one tile per item, so a grid index is an index into
 * [MediaSelectState.Files.selectedMediaFolderItems].
 */
@Composable
private fun rememberDragToSelectMediaState(
  state: MediaSelectState,
  onEvent: (MediaSelectScreenEvents) -> Unit,
  gridState: LazyGridState
): DragToSelectState {
  return rememberDragToSelectState(gridState) { event ->
    val items = (state as? MediaSelectState.Files)?.selectedMediaFolderItems ?: return@rememberDragToSelectState

    when (event) {
      is DragSelectEvent.Started -> {
        // A range only ever grows out from an unselected tile. Long pressing one that is already selected unselects it
        // and stops there, which is how the v2 gallery behaved.
        val media = items.getOrNull(event.index)
        if (media != null && state.selectedMedia.any { it.uri == media.uri }) {
          onEvent(MediaSelectScreenEvents.MediaUnselected(setOf(media)))
          cancel()
        }
      }

      is DragSelectEvent.RangeSelected -> {
        val media = event.indices.mapNotNullTo(mutableSetOf(), items::getOrNull)
        if (media.isNotEmpty()) {
          onEvent(MediaSelectScreenEvents.MediaSelected(media))
        }
      }

      is DragSelectEvent.RangeUnselected -> {
        val media = event.indices.mapNotNullTo(mutableSetOf(), items::getOrNull)
        if (media.isNotEmpty()) {
          onEvent(MediaSelectScreenEvents.MediaUnselected(media))
        }
      }
    }
  }
}

@Composable
private fun rememberGridConfiguration(isRootGrid: Boolean): GridConfiguration {
  val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass

  return remember(windowSizeClass, isRootGrid) {
    GridConfiguration(
      gridCells = if (isRootGrid) {
        windowSizeClass.forWidthBreakpoint(
          expanded = GridCells.Fixed(6),
          medium = GridCells.Fixed(4),
          compact = GridCells.Fixed(2)
        )
      } else {
        windowSizeClass.forWidthBreakpoint(
          expanded = GridCells.Fixed(8),
          medium = GridCells.Fixed(6),
          compact = GridCells.Fixed(4)
        )
      },
      horizontalMargin = if (isRootGrid) {
        windowSizeClass.forWidthBreakpoint(
          expanded = 38.dp,
          medium = 35.dp,
          compact = 24.dp
        )
      } else {
        0.dp
      },
      horizontalSpacing = if (isRootGrid) {
        windowSizeClass.forWidthBreakpoint(
          expanded = 32.dp,
          medium = 28.dp,
          compact = 16.dp
        )
      } else {
        4.dp
      },
      verticalSpacing = if (isRootGrid) {
        windowSizeClass.forWidthBreakpoint(
          expanded = 32.dp,
          medium = 32.dp,
          compact = 24.dp
        )
      } else {
        4.dp
      },
      bottomBarVerticalPadding = windowSizeClass.forWidthBreakpoint(
        expanded = 16.dp,
        medium = 16.dp,
        compact = 8.dp
      ),
      bottomBarHorizontalPadding = windowSizeClass.forWidthBreakpoint(
        expanded = 24.dp,
        medium = 24.dp,
        compact = 16.dp
      ),
      bottomBarAlignment = windowSizeClass.forWidthBreakpoint(
        expanded = Alignment.End,
        medium = Alignment.End,
        compact = Alignment.Start
      )
    )
  }
}

private fun <T> WindowSizeClass.forWidthBreakpoint(
  expanded: T,
  medium: T,
  compact: T
): T {
  return when {
    isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) -> expanded
    isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> medium
    else -> compact
  }
}

/**
 * Persistent reminder that we are only seeing the media the user handed us, shown above the grid whenever
 * selected-photos access has produced something to browse.
 */
@Composable
private fun LimitedAccessBar(onEvent: (MediaSelectScreenEvents) -> Unit) {
  val menuController = remember { DropdownMenus.MenuController() }

  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 72.dp)
      .padding(16.dp)
  ) {
    Text(
      text = stringResource(R.string.MediaSelectScreen__signal_has_limited_access),
      style = MaterialTheme.typography.bodyMedium,
      modifier = Modifier
        .weight(1f)
        .padding(end = 16.dp)
    )

    Box {
      Buttons.MediumTonal(onClick = menuController::toggle) {
        Text(text = stringResource(R.string.MediaSelectScreen__manage))
      }

      ManageAccessMenu(menuController = menuController, onEvent = onEvent)
    }
  }
}

/**
 * Shown over the placeholder grid when we have nothing of the user's to display: either we cannot read their media
 * at all and have to ask, or they granted selected-photos access without sharing anything we can use.
 */
@Composable
private fun MediaAccessCallToAction(
  mediaPermissions: MediaPermissions,
  onEvent: (MediaSelectScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  val menuController = remember { DropdownMenus.MenuController() }
  val hasNoAccess = mediaPermissions == MediaPermissions.NONE

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = modifier.padding(horizontal = 30.dp)
  ) {
    Image(
      painter = painterResource(R.drawable.permission_gallery),
      contentDescription = null
    )

    Text(
      text = stringResource(
        if (hasNoAccess) {
          R.string.MediaSelectScreen__signal_needs_permission_to_show_your_photos_and_videos
        } else {
          R.string.MediaSelectScreen__no_photos_or_videos_found
        }
      ),
      style = MaterialTheme.typography.bodyLarge,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(top = 20.dp)
    )

    Box(modifier = Modifier.padding(top = 20.dp)) {
      if (hasNoAccess) {
        Buttons.LargeTonal(onClick = { onEvent(MediaSelectScreenEvents.RequestMediaPermissions) }) {
          Text(text = stringResource(R.string.MediaSelectScreen__allow_access))
        }
      } else {
        Buttons.LargeTonal(onClick = menuController::toggle) {
          Text(text = stringResource(R.string.MediaSelectScreen__manage))
        }

        ManageAccessMenu(menuController = menuController, onEvent = onEvent)
      }
    }
  }
}

/**
 * The two ways out of selected-photos access: widen the selection through the system prompt, or go turn the
 * permission up in app settings.
 */
@Composable
private fun ManageAccessMenu(
  menuController: DropdownMenus.MenuController,
  onEvent: (MediaSelectScreenEvents) -> Unit
) {
  val context = LocalContext.current

  DropdownMenus.Menu(controller = menuController, offsetX = 0.dp, offsetY = 8.dp) { controller ->
    DropdownMenus.ItemWithIcon(
      menuController = controller,
      drawableResId = R.drawable.symbol_album_tilt_24,
      stringResId = R.string.MediaSelectScreen__select_more_photos,
      onClick = { onEvent(MediaSelectScreenEvents.SelectMorePhotos) }
    )

    DropdownMenus.ItemWithIcon(
      menuController = controller,
      drawableResId = org.signal.core.ui.R.drawable.symbol_settings_android_24,
      stringResId = R.string.MediaSelectScreen__go_to_settings,
      onClick = { context.startActivity(PermissionsUtil.getApplicationSettingsIntent(context)) }
    )
  }
}

/**
 * Empty tile standing in for media we are not allowed to see, so the call to action reads as a gallery we could
 * be looking at rather than a blank screen.
 */
@Composable
private fun MediaTilePlaceholder() {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .aspectRatio(1f)
      .background(color = MaterialTheme.colorScheme.surfaceVariant)
  )
}

@Composable
private fun MediaFolderTile(
  mediaFolder: MediaFolder,
  onEvent: (MediaSelectScreenEvents) -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(
        onClick = { onEvent(MediaSelectScreenEvents.FolderClick(mediaFolder)) },
        onClickLabel = mediaFolder.title,
        role = Role.Button
      ),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    if (LocalInspectionMode.current) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .aspectRatio(1f)
          .background(color = Previews.rememberRandomColor(), shape = RoundedCornerShape(26.dp))
      )
    } else {
      BoxWithConstraints(
        modifier = Modifier
          .fillMaxWidth()
          .aspectRatio(1f)
          .clip(RoundedCornerShape(26.dp))
      ) {
        val width = maxWidth
        val height = maxHeight

        GlideImage(
          model = mediaFolder.thumbnailUri,
          imageSize = DpSize(width, height),
          modifier = Modifier
            .aspectRatio(1f)
        )
      }
    }

    Text(text = mediaFolder.title, modifier = Modifier.padding(top = 12.dp))
  }
}

/**
 * @param recipientChatColor The chat color of the single recipient this media is headed to, or null when the
 *   destination is still to be chosen and the selection badge falls back to the theme.
 */
@Composable
private fun MediaTile(
  media: Media,
  selectionIndex: Int,
  onEvent: (MediaSelectScreenEvents) -> Unit,
  recipientChatColor: Color? = null
) {
  val scale by animateFloatAsState(
    targetValue = if (selectionIndex >= 0) {
      0.8f
    } else {
      1f
    }
  )

  val outerCornerClip by animateDpAsState(
    if (selectionIndex >= 0) 0.dp else 2.dp
  )

  val cornerClip by animateDpAsState(
    if (selectionIndex >= 0) 12.dp else 2.dp
  )

  // Square regardless of what is in it. The thumbnail emits nothing until it has loaded, so a tile sized by its content
  // is zero-height until then: the row collapses, and the tile cannot be hit by a gesture that goes by item geometry.
  BoxWithConstraints(
    modifier = Modifier
      .fillMaxWidth()
      .aspectRatio(1f)
      .background(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(outerCornerClip))
      .clickable(
        onClick = { onEvent(MediaSelectScreenEvents.MediaClick(media)) },
        onClickLabel = media.fileName,
        role = Role.Button
      )
  ) {
    val width = maxWidth
    val height = maxHeight

    Box(
      modifier = Modifier
        .aspectRatio(1f)
        .scale(scale)
        .clip(RoundedCornerShape(cornerClip))
    ) {
      if (LocalInspectionMode.current) {
        Box(
          modifier = Modifier
            .background(color = Previews.rememberRandomColor())
            .fillMaxWidth()
        )
      } else {
        GlideImage(
          model = media.uri,
          imageSize = DpSize(width, height),
          modifier = Modifier.aspectRatio(1f)
        )
      }

      MediaTileVideoOverlay(media)
    }
  }

  if (selectionIndex >= 0) {
    Box(
      modifier = Modifier.padding(3.dp)
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .border(width = 3.dp, color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(percent = 50))
          .padding(1.dp)
          .background(color = recipientChatColor ?: MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(percent = 50))
          .ensureWidthIsAtLeastHeight()
          .padding(horizontal = 5.5.dp, vertical = 2.dp)
      ) {
        Text(
          text = "${selectionIndex + 1}",
          color = if (recipientChatColor != null) SignalTheme.colors.colorOnCustom else MaterialTheme.colorScheme.onPrimary
        )
      }
    }
  }
}

@Composable
private fun MediaTileVideoOverlay(
  media: Media
) {
  if (ContentTypeUtil.isVideo(media.contentType) && !media.isVideoGif) {
    Box(
      contentAlignment = Alignment.TopEnd,
      modifier = Modifier
        .fillMaxWidth()
        .background(
          brush = Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.4f),
            1f to Color.Transparent
          )
        )
        .padding(top = 8.dp, end = 8.dp, bottom = 26.dp)
    ) {
      Text(
        text = remember(media.duration) { media.duration.milliseconds.formatAsClock() },
        style = MaterialTheme.typography.labelLarge,
        color = SignalTheme.colors.colorOnCustom
      )
    }
  }
}

@Composable
private fun NextButton(mediaSelectionCount: Int, recipientChatColor: Color? = null, onClick: () -> Unit) {
  Buttons.MediumTonal(
    onClick = onClick,
    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
  ) {
    Box(
      modifier = Modifier
        .background(color = recipientChatColor ?: MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(percent = 50))
        .ensureWidthIsAtLeastHeight()
    ) {
      Text(
        text = "$mediaSelectionCount",
        color = if (recipientChatColor != null) SignalTheme.colors.colorOnCustom else MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier
      )
    }

    Icon(
      imageVector = ImageVector.vectorResource(org.signal.core.ui.R.drawable.symbol_chevron_right_24),
      contentDescription = stringResource(R.string.MediaSelectScreen__next)
    )
  }
}

/**
 * The rail of currently selected media. Items can be long pressed and dragged to change the order they'll be sent in.
 */
@Composable
private fun SelectedMediaRow(
  selectedMedia: List<Media>,
  selectionAdditions: Flow<Media>,
  alignment: Alignment.Horizontal,
  onEvent: (MediaSelectScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  val listState = rememberLazyListState()
  val reorderBuffer = rememberReorderBuffer(selectedMedia) { fromIndex, toIndex ->
    onEvent(MediaSelectScreenEvents.ReorderSelectedMedia(fromIndex, toIndex))
  }
  val reorderableListState = rememberReorderableListState(
    lazyListState = listState,
    includeHeader = false,
    includeFooter = false,
    orientation = Orientation.Horizontal,
    onEvent = reorderBuffer::onReorderListEvent
  )

  LaunchedEffect(listState, selectionAdditions) {
    selectionAdditions.collect { addition ->
      // Located in the order the rail is rendering rather than in the selection, which a drag in progress is holding
      // back. The addition is also announced with the state that carries it, ahead of the layout that measures it, and a
      // list asked for an item it has not measured yet believes it is already at its end and gives up without moving.
      val index = snapshotFlow {
        val index = reorderBuffer.items.indexOfFirst { it.uri == addition.uri }
        if (index < listState.layoutInfo.totalItemsCount) index else -1
      }.first { it >= 0 }

      try {
        listState.animateScrollToItem(index)
      } catch (e: CancellationException) {
        // A touch on the rail preempts the scroll and cancels it. That is the user taking the rail over, and it ends
        // this scroll rather than our interest in the next addition.
        currentCoroutineContext().ensureActive()
      }
    }
  }

  LazyRow(
    state = listState,
    modifier = modifier.reorderableList(reorderableListState),
    horizontalArrangement = spacedBy(space = 12.dp, alignment = alignment)
  ) {
    itemsIndexed(reorderBuffer.items, key = { _, media -> media.uri }) { index, media ->
      ReorderableItem(reorderableListState, index) {
        MediaThumbnail(
          media = media,
          modifier = Modifier.testTag(TestTags.selectedMediaThumbnail(media.uri.toString()))
        ) {
          onEvent(MediaSelectScreenEvents.SetFocusedMedia(media))
          onEvent(MediaSelectScreenEvents.NavigateToEdit)
        }
      }
    }
  }
}

@Composable
private fun MediaThumbnail(
  media: Media,
  modifier: Modifier = Modifier,
  onClick: () -> Unit
) {
  // Sized by the box rather than by what ends up in it. The thumbnail emits nothing at all until it has loaded, and an
  // item with no width until then is one the rail cannot lay out, scroll to, or show.
  Box(
    modifier = modifier
      .size(MediaSendMetrics.SelectedMediaPreviewSize)
      .clip(RoundedCornerShape(8.dp))
      .background(color = MaterialTheme.colorScheme.surfaceVariant)
      .clickable(onClick = onClick, onClickLabel = media.fileName, role = Role.Button)
  ) {
    if (LocalInspectionMode.current) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(color = Previews.rememberRandomColor())
      )
    } else {
      GlideImage(
        model = media.uri,
        imageSize = MediaSendMetrics.SelectedMediaPreviewSize,
        modifier = Modifier.fillMaxSize()
      )
    }
  }
}

@AllDevicePreviews
@Composable
private fun MediaSelectScreenFolderPreview() {
  Previews.Preview {
    MediaSelectScreen(
      state = MediaSelectState.Folders(
        mediaFolders = rememberPreviewMediaFolders(20),
        selectedMedia = emptyList()
      ),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun MediaSelectScreenMediaPreview() {
  val folders = rememberPreviewMediaFolders(20)
  val media = rememberPreviewMedia(100)
  val selectedMedia: MutableList<Media> = remember { mutableStateListOf() }

  Previews.Preview {
    MediaSelectScreen(
      state = MediaSelectState.Files(
        selectedMediaFolder = folders.first(),
        selectedMediaFolderItems = media,
        selectedMedia = selectedMedia
      ),
      onEvent = {
        if (it is MediaSelectScreenEvents.MediaClick) {
          if (it.media in selectedMedia) {
            selectedMedia.remove(it.media)
          } else {
            selectedMedia.add(it.media)
          }
        }
      }
    )
  }
}

@AllDevicePreviews
@Composable
private fun MediaSelectScreenNoPermissionPreview() {
  Previews.Preview {
    MediaSelectScreen(
      state = MediaSelectState.Folders(
        mediaFolders = emptyList(),
        selectedMedia = emptyList(),
        mediaPermissions = MediaPermissions.NONE
      ),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun MediaSelectScreenPartialPermissionEmptyPreview() {
  Previews.Preview {
    MediaSelectScreen(
      state = MediaSelectState.Folders(
        mediaFolders = emptyList(),
        selectedMedia = emptyList(),
        mediaPermissions = MediaPermissions.PARTIAL
      ),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun MediaSelectScreenPartialPermissionPreview() {
  Previews.Preview {
    MediaSelectScreen(
      state = MediaSelectState.Folders(
        mediaFolders = rememberPreviewMediaFolders(4),
        selectedMedia = emptyList(),
        mediaPermissions = MediaPermissions.PARTIAL
      ),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun MediaFolderTilePreview() {
  Previews.Preview {
    Box(modifier = Modifier.width(174.dp)) {
      MediaFolderTile(
        mediaFolder = rememberPreviewMediaFolders(1).first(),
        onEvent = {}
      )
    }
  }
}

@DayNightPreviews
@Composable
private fun MediaTilePreview() {
  Previews.Preview {
    MediaTile(
      media = rememberPreviewMedia(1).first(),
      selectionIndex = -1,
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun MediaTileSelectedPreview() {
  var isSelected by remember { mutableStateOf(true) }

  Previews.Preview {
    MediaTile(
      media = rememberPreviewMedia(1).first(),
      selectionIndex = if (isSelected) 0 else -1,
      onEvent = {
        if (it is MediaSelectScreenEvents.MediaClick) {
          isSelected = !isSelected
        }
      }
    )
  }
}

@DayNightPreviews
@Composable
private fun MediaTileSelectedChatColorPreview() {
  Previews.Preview {
    MediaTile(
      media = rememberPreviewMedia(1).first(),
      selectionIndex = 0,
      onEvent = {},
      recipientChatColor = Color(0xFF3B7845)
    )
  }
}

@DayNightPreviews
@Composable
private fun NextButtonPreview() {
  Previews.Preview {
    NextButton(
      mediaSelectionCount = 3,
      onClick = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun NextButtonChatColorPreview() {
  Previews.Preview {
    NextButton(
      mediaSelectionCount = 3,
      recipientChatColor = Color(0xFF3B7845),
      onClick = {}
    )
  }
}

@Composable
private fun rememberPreviewMediaFolders(count: Int): List<MediaFolder> {
  return remember(count) {
    (0 until count).map { index ->
      MediaFolder(
        thumbnailUri = "https://example.com/folder$index.jpg".toUri(),
        title = "Folder $index",
        itemCount = (index + 1) * 10,
        bucketId = "bucket_$index",
        folderType = if (index == 0) MediaFolder.FolderType.CAMERA else MediaFolder.FolderType.NORMAL
      )
    }
  }
}

private data class GridConfiguration(
  val gridCells: GridCells,
  val horizontalMargin: Dp,
  val horizontalSpacing: Dp,
  val verticalSpacing: Dp,
  val bottomBarVerticalPadding: Dp,
  val bottomBarHorizontalPadding: Dp,
  val bottomBarAlignment: Alignment.Horizontal
)
