/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediaviewer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.distinctUntilChanged
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.signal.mediaviewer.components.ExpandingCaption
import org.signal.mediaviewer.components.MediaViewerBottomBar
import org.signal.mediaviewer.components.MediaViewerTopBar
import org.signal.mediaviewer.pages.MediaViewerPage
import org.signal.mediaviewer.playback.MediaViewerPlaybackController
import org.signal.mediaviewer.playback.PlaybackUiState

private const val CHROME_BACKGROUND_ALPHA = 0.87f

/** How much faster than the backdrop the chrome fades out during a drag-to-dismiss. */
private const val CHROME_DISMISS_FADE_RATE = 4f

/**
 * Full-screen media viewer: a pager over a thread's media with zooming images, video playback,
 * captions, an album rail, and share/forward/save/delete/edit actions.
 */
@Composable
fun MediaViewerScreen(
  state: MediaViewerState,
  onEvent: (MediaViewerEvent) -> Unit,
  modifier: Modifier = Modifier,
  playbackController: MediaViewerPlaybackController = remember { MediaViewerPlaybackController() },
  dismissState: DragToDismissState = rememberDragToDismissState()
) {
  LaunchedEffect(playbackController) {
    playbackController.runPositionTicker()
  }

  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner, playbackController) {
    val observer = LifecycleEventObserver { _, lifecycleEvent ->
      when (lifecycleEvent) {
        Lifecycle.Event.ON_PAUSE -> playbackController.pause()
        Lifecycle.Event.ON_RESUME -> playbackController.resumeIfLooping()
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  // Deliberately no backdrop here: the host draws the background and its transition image beneath
  // this screen, so any opaque compose layer would cover the transition image before the media has
  // rendered, flashing the theme background.
  Box(
    modifier = modifier
      .fillMaxSize()
      .testTag(MediaViewerScreenTestTags.ROOT)
  ) {
    MediaPager(
      state = state,
      playbackController = playbackController,
      dismissState = dismissState,
      onEvent = onEvent,
      modifier = Modifier.fillMaxSize()
    )

    // The chrome fades rather than leaving composition, so the rail keeps its scroll position,
    // thumbnails, and selection across a hide and show.
    val chromeVisibilityAlpha by animateFloatAsState(
      targetValue = if (state.chromeVisible) 1f else 0f,
      animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
      label = "chromeAlpha"
    )
    // Dragging to dismiss fades the chrome out well before the backdrop, leaving just the media.
    val chromeAlpha = { chromeVisibilityAlpha * (1f - dismissState.progress * CHROME_DISMISS_FADE_RATE).coerceAtLeast(0f) }

    MediaViewerTopBar(
      item = state.currentItem,
      isDraft = state.isDraft,
      showThread = state.showThread,
      playbackController = playbackController,
      onEvent = onEvent,
      modifier = Modifier
        .align(Alignment.TopCenter)
        .fadingChrome(chromeAlpha)
        .background(MaterialTheme.colorScheme.surface.copy(alpha = CHROME_BACKGROUND_ALPHA))
        .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)))
    )

    Column(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .fadingChrome(chromeAlpha)
        .background(MaterialTheme.colorScheme.surface.copy(alpha = CHROME_BACKGROUND_ALPHA))
        .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)))
        .fillMaxWidth()
    ) {
      AnimatedContent(
        targetState = state.currentCaption?.takeIf { it.text.isNotEmpty() },
        transitionSpec = { fadeIn() togetherWith fadeOut() using SizeTransform() },
        contentKey = { it?.text }
      ) { caption ->
        if (caption != null) {
          ExpandingCaption(caption = caption)
        } else {
          Spacer(modifier = Modifier.fillMaxWidth())
        }
      }

      MediaViewerBottomBar(
        controlMode = state.controlMode,
        railItems = state.railItems,
        selectedRailItem = state.currentItem,
        playbackController = playbackController,
        onEvent = onEvent
      )
    }
  }

  MediaViewerDialogs(dialog = state.dialog, onEvent = onEvent)
}

@Composable
private fun MediaPager(
  state: MediaViewerState,
  playbackController: MediaViewerPlaybackController,
  dismissState: DragToDismissState,
  onEvent: (MediaViewerEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  if (state.items.isEmpty()) {
    Box(modifier = modifier)
    return
  }

  val pagerState = rememberPagerState(initialPage = state.position.coerceIn(0, state.items.lastIndex)) { state.items.size }

  LaunchedEffect(pagerState) {
    snapshotFlow { pagerState.currentPage }
      .distinctUntilChanged()
      .collect { onEvent(MediaViewerEvent.PageScrolled(it)) }
  }

  LaunchedEffect(pagerState) {
    snapshotFlow { pagerState.settledPage }
      .distinctUntilChanged()
      .collect { onEvent(MediaViewerEvent.PageSettled(it)) }
  }

  LaunchedEffect(pagerState) {
    snapshotFlow { pagerState.isScrollInProgress }
      .distinctUntilChanged()
      .collect { onEvent(MediaViewerEvent.PagerScrollStateChanged(it)) }
  }

  // requestScrollToPage lands in the same remeasure as the re-index, so the settled page survives it.
  val immediateScrollRequest = state.scrollRequest?.takeUnless { it.animated }
  val lastImmediateScrollId = remember { LastImmediateScrollId() }
  if (immediateScrollRequest != null && lastImmediateScrollId.value != immediateScrollRequest.id) {
    lastImmediateScrollId.value = immediateScrollRequest.id
    pagerState.requestScrollToPage(immediateScrollRequest.position.coerceIn(0, state.items.lastIndex))
  }

  // Programmatic jumps arrive only as scroll requests, keyed by id so each fires exactly once and
  // a stale state emission can never yank the pager mid-gesture.
  LaunchedEffect(state.scrollRequest?.id) {
    val request = state.scrollRequest ?: return@LaunchedEffect
    val target = request.position.coerceIn(0, state.items.lastIndex)
    if (target != pagerState.currentPage) {
      onEvent(MediaViewerEvent.ScrollRequestStarted(request.id))
      if (request.animated) {
        pagerState.animateScrollToPage(target)
      } else {
        pagerState.requestScrollToPage(target)
      }
    } else if (!pagerState.isScrollInProgress) {
      onEvent(MediaViewerEvent.PageSettled(target))
    }
  }

  val currentState by rememberUpdatedState(state)
  val currentOnEvent by rememberUpdatedState(onEvent)

  // The item list is already in display order (the data layer applies leftIsRecent), so the pager
  // itself always lays out left-to-right.
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    HorizontalPager(
      state = pagerState,
      pageSpacing = 24.dp,
      beyondViewportPageCount = 1,
      key = { index -> state.items.getOrNull(index)?.attachmentId?.id ?: -(index + 1L) },
      modifier = modifier
        .dragToDismiss(
          state = dismissState,
          canStart = { !currentState.isInSharedAnimation && !pagerState.isScrollInProgress && !currentState.isCurrentMediaZoomedIn },
          canDismissToSharedElement = { currentState.canDismissToSharedElement },
          onDismissed = { toSharedElement -> currentOnEvent(MediaViewerEvent.DragDismissed(toSharedElement)) }
        )
        .graphicsLayer {
          alpha = if (state.isInSharedAnimation) 0f else 1f
          translationY = dismissState.offsetY
        }
        .testTag(MediaViewerScreenTestTags.PAGER)
    ) { page ->
      MediaViewerPage(
        item = state.items.getOrNull(page),
        isActive = pagerState.settledPage == page,
        playbackController = playbackController,
        onEvent = onEvent
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MediaViewerDialogs(dialog: MediaViewerState.Dialog, onEvent: (MediaViewerEvent) -> Unit) {
  when (dialog) {
    MediaViewerState.Dialog.None -> Unit
    is MediaViewerState.Dialog.DeleteConfirmation -> {
      Dialogs.BaseAlertDialog(
        onDismissRequest = { onEvent(MediaViewerEvent.DialogDismissed) },
        title = { Text(text = stringResource(R.string.MediaViewerScreen__media_delete_confirmation_title)) },
        text = { Text(text = stringResource(R.string.MediaViewerScreen__media_delete_confirmation_message)) },
        confirmButton = {
          FlowRow {
            if (dialog.canDeleteForEveryone) {
              TextButton(
                onClick = { onEvent(MediaViewerEvent.DeleteConfirmed(deleteForEveryone = true)) },
                modifier = Modifier.testTag(MediaViewerScreenTestTags.DELETE_FOR_EVERYONE_BUTTON)
              ) {
                Text(text = stringResource(R.string.MediaViewerScreen__delete_for_everyone))
              }
            }

            TextButton(onClick = { onEvent(MediaViewerEvent.DialogDismissed) }) {
              Text(text = stringResource(R.string.MediaViewerScreen__cancel))
            }

            TextButton(
              onClick = { onEvent(MediaViewerEvent.DeleteConfirmed(deleteForEveryone = false)) },
              modifier = Modifier.testTag(MediaViewerScreenTestTags.DELETE_BUTTON)
            ) {
              Text(
                text = stringResource(
                  if (dialog.isNoteToSelf) {
                    R.string.MediaViewerScreen__delete_dialog_delete
                  } else {
                    R.string.MediaViewerScreen__delete_for_me
                  }
                )
              )
            }
          }
        },
        modifier = Modifier.testTag(MediaViewerScreenTestTags.DELETE_DIALOG)
      )
    }
    MediaViewerState.Dialog.SaveOffloadedMedia -> {
      Dialogs.SimpleAlertDialog(
        title = stringResource(R.string.MediaViewerScreen__cant_save_media),
        body = stringResource(R.string.MediaViewerScreen__media_offloaded_message),
        confirm = stringResource(R.string.MediaViewerScreen__ok),
        onConfirm = { onEvent(MediaViewerEvent.DialogDismissed) },
        dismiss = stringResource(R.string.MediaViewerScreen__view_storage_settings),
        onDeny = { onEvent(MediaViewerEvent.ViewStorageSettingsClicked) },
        onDismiss = { onEvent(MediaViewerEvent.DialogDismissed) },
        modifier = Modifier.testTag(MediaViewerScreenTestTags.OFFLOADED_MEDIA_DIALOG)
      )
    }
  }
}

object MediaViewerScreenTestTags {
  const val ROOT = "media-viewer-root"
  const val PAGER = "media-viewer-pager"
  const val PAGE_IMAGE = "media-viewer-page-image"
  const val PAGE_VIDEO = "media-viewer-page-video"
  const val PAGE_PLACEHOLDER = "media-viewer-page-placeholder"
  const val TOP_BAR = "media-viewer-top-bar"
  const val BACK_BUTTON = "media-viewer-back-button"
  const val TOOLBAR_TITLE = "media-viewer-toolbar-title"
  const val OVERFLOW_BUTTON = "media-viewer-overflow-button"
  const val MENU_EDIT = "media-viewer-menu-edit"
  const val MENU_SAVE = "media-viewer-menu-save"
  const val MENU_DELETE = "media-viewer-menu-delete"
  const val SHARE_BUTTON = "media-viewer-share-button"
  const val FORWARD_BUTTON = "media-viewer-forward-button"
  const val PLAY_PAUSE_BUTTON = "media-viewer-play-pause-button"
  const val REWIND_BUTTON = "media-viewer-rewind-button"
  const val FAST_FORWARD_BUTTON = "media-viewer-fast-forward-button"
  const val SEEK_BAR = "media-viewer-seek-bar"
  const val POSITION_LABEL = "media-viewer-position-label"
  const val DURATION_LABEL = "media-viewer-duration-label"
  const val CAPTION = "media-viewer-caption"
  const val ALBUM_RAIL = "media-viewer-album-rail"
  const val DELETE_DIALOG = "media-viewer-delete-dialog"
  const val DELETE_BUTTON = "media-viewer-delete-button"
  const val DELETE_FOR_EVERYONE_BUTTON = "media-viewer-delete-for-everyone-button"
  const val OFFLOADED_MEDIA_DIALOG = "media-viewer-offloaded-media-dialog"

  fun railItemTag(item: MediaViewerItem?): String {
    return "media-viewer-rail-item-${item?.attachmentId?.id ?: "placeholder"}"
  }
}

@AllDevicePreviews
@Composable
private fun MediaViewerScreenPreview() {
  Previews.Preview {
    MediaViewerScreen(
      state = MediaViewerState(
        loadState = MediaViewerState.LoadState.MEDIA_READY,
        items = MediaViewerPreviewFixtures.previewItems(3),
        position = 1,
        isInSharedAnimation = false
      ),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun MediaViewerScreenCaptionPreview() {
  Previews.Preview {
    MediaViewerScreen(
      state = MediaViewerState(
        loadState = MediaViewerState.LoadState.MEDIA_READY,
        items = MediaViewerPreviewFixtures.previewItems(1).map { it?.copy(caption = MediaViewerPreviewFixtures.LOREM_IPSUM) },
        position = 0,
        isInSharedAnimation = false
      ),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun MediaViewerScreenDeleteDialogPreview() {
  Previews.Preview {
    MediaViewerScreen(
      state = MediaViewerState(
        loadState = MediaViewerState.LoadState.MEDIA_READY,
        items = MediaViewerPreviewFixtures.previewItems(1),
        isInSharedAnimation = false,
        dialog = MediaViewerState.Dialog.DeleteConfirmation(canDeleteForEveryone = true, isNoteToSelf = false)
      ),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun MediaViewerScreenLoadingPreview() {
  Previews.Preview {
    MediaViewerScreen(
      state = MediaViewerState(
        items = listOf(null),
        isInSharedAnimation = false
      ),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun MediaViewerScreenVideoPreview() {
  Previews.Preview {
    MediaViewerScreen(
      state = MediaViewerState(
        loadState = MediaViewerState.LoadState.MEDIA_READY,
        items = listOf(MediaViewerPreviewFixtures.previewItem(contentType = "video/mp4")),
        isInSharedAnimation = false
      ),
      onEvent = {},
      playbackController = remember {
        MediaViewerPlaybackController(PlaybackUiState(isAttached = true, isPlaying = true, positionMs = 7_000, durationMs = 112_000))
      }
    )
  }
}

@AllDevicePreviews
@Composable
private fun MediaViewerScreenDraftPreview() {
  Previews.Preview {
    MediaViewerScreen(
      state = MediaViewerState(
        loadState = MediaViewerState.LoadState.MEDIA_READY,
        items = listOf(MediaViewerPreviewFixtures.previewItem()),
        isDraft = true,
        isInSharedAnimation = false
      ),
      onEvent = {}
    )
  }
}

/**
 * Applies [alpha] in the layout phase so a fade never recomposes the content. Fully hidden
 * content is left unplaced, so it neither draws nor takes touches away from the pager.
 */
private fun Modifier.fadingChrome(alpha: () -> Float): Modifier {
  return layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) {
      val currentAlpha = alpha()
      if (currentAlpha > 0f) {
        placeable.placeWithLayer(0, 0) { this.alpha = currentAlpha }
      }
    }
  }
}

/** Composition-scoped bookkeeping so a composition-time [androidx.compose.foundation.pager.PagerState.requestScrollToPage] fires once per request. */
private class LastImmediateScrollId(var value: Long? = null)
