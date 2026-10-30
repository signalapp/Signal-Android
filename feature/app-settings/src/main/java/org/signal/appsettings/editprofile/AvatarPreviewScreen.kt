/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.editprofile

import androidx.annotation.VisibleForTesting
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.compose.dragToDismiss
import org.signal.core.ui.compose.rememberDragToDismissState
import org.signal.core.ui.R as CoreUiR

private const val MAX_SCALE = 8f
private const val DOUBLE_TAP_SCALE = 3f

private val ToolbarScrim = Color(0x90010101)

@VisibleForTesting
object AvatarPreviewTestTags {
  const val AVATAR = "avatar"
  const val TITLE = "title"
  const val BUTTON_BACK = "button-back"
}

/**
 * The user's avatar, filling as much of the window as it can on a dark background, which can be zoomed by pinching or
 * double-tapping, and dragged away to dismiss back into wherever it came from.
 *
 * @param title the user's name, shown in the toolbar.
 * @param toolbarVisible whether the toolbar is showing, which tapping the avatar toggles.
 * @param avatar the avatar, drawn to fill the modifier it's handed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarPreviewScreen(
  title: String,
  toolbarVisible: Boolean,
  onEvent: (AvatarPreviewEvent) -> Unit,
  modifier: Modifier = Modifier,
  avatar: @Composable (Modifier) -> Unit = {}
) {
  val dismissState = rememberDragToDismissState()
  val zoom = rememberZoomState()
  val scope = rememberCoroutineScope()
  val currentOnEvent by rememberUpdatedState(onEvent)

  Box(modifier = modifier.fillMaxSize()) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .graphicsLayer { alpha = 1f - dismissState.progress }
        .background(colorResource(CoreUiR.color.core_grey_95))
    )

    BoxWithConstraints(
      contentAlignment = Alignment.Center,
      modifier = Modifier
        .fillMaxSize()
        .dragToDismiss(
          state = dismissState,
          canStart = { zoom.scale.value <= 1f },
          canDismissToSharedElement = { true },
          onDismissed = { currentOnEvent(AvatarPreviewEvent.AvatarDraggedAway) }
        )
    ) {
      Box(
        modifier = Modifier
          .size(min(maxWidth, maxHeight))
          .graphicsLayer {
            translationX = dismissState.offsetX
            translationY = dismissState.offsetY
            scaleX = dismissState.scale
            scaleY = dismissState.scale
          }
          .pointerInput(Unit) {
            detectTapGestures(
              onTap = { currentOnEvent(AvatarPreviewEvent.AvatarClicked) },
              onDoubleTap = { tap -> zoom.toggle(scope, tap, size) }
            )
          }
          .pointerInput(Unit) { detectPinchAndPan(zoom, scope) }
          .testTag(AvatarPreviewTestTags.AVATAR)
      ) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
              scaleX = zoom.scale.value
              scaleY = zoom.scale.value
              translationX = zoom.offset.value.x
              translationY = zoom.offset.value.y
            }
        ) {
          avatar(Modifier.fillMaxSize())
        }
      }
    }

    AnimatedVisibility(
      visible = toolbarVisible && !dismissState.isDismissing,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier.graphicsLayer { alpha = 1f - dismissState.progress }
    ) {
      TopAppBar(
        title = {
          Text(
            text = title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag(AvatarPreviewTestTags.TITLE)
          )
        },
        navigationIcon = {
          IconButton(
            onClick = { onEvent(AvatarPreviewEvent.NavigateBackClicked) },
            modifier = Modifier.testTag(AvatarPreviewTestTags.BUTTON_BACK)
          ) {
            Icon(
              imageVector = SignalIcons.ArrowStart.imageVector,
              contentDescription = stringResource(CoreUiR.string.DefaultTopAppBar__navigate_up_content_description)
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = ToolbarScrim,
          titleContentColor = Color.White,
          navigationIconContentColor = Color.White
        )
      )
    }
  }
}

/**
 * Pinches, and pans while zoomed in. A one-finger drag at rest is left alone, so that it can drag the avatar away.
 */
private suspend fun PointerInputScope.detectPinchAndPan(zoom: ZoomState, scope: CoroutineScope) {
  awaitEachGesture {
    awaitFirstDown(requireUnconsumed = false)
    do {
      val event = awaitPointerEvent()
      val pressed = event.changes.count { it.pressed }
      if (pressed > 1 || zoom.scale.value > 1f) {
        zoom.transform(scope, event.calculateCentroid(), event.calculatePan(), event.calculateZoom(), size)
        event.changes.forEach { if (it.positionChanged()) it.consume() }
      }
    } while (event.changes.any { it.pressed })
  }
}

@Composable
private fun rememberZoomState(): ZoomState = remember { ZoomState() }

/** How far the avatar is zoomed, and where to. The offset is from the avatar's resting place, in pixels. */
private class ZoomState {
  val scale = Animatable(1f)
  val offset = Animatable(Offset.Zero, Offset.VectorConverter)

  /** Zooms in on [tap] when the avatar is at rest, or back out when it isn't. */
  fun toggle(scope: CoroutineScope, tap: Offset, size: IntSize) {
    val targetScale = if (scale.value > 1f) 1f else DOUBLE_TAP_SCALE
    val targetOffset = clamp((center(size) - tap) * (targetScale - 1f), targetScale, size)

    scope.launch { scale.animateTo(targetScale) }
    scope.launch { offset.animateTo(targetOffset) }
  }

  /** Applies a pinch, keeping the point under [centroid] where it is. */
  fun transform(scope: CoroutineScope, centroid: Offset, pan: Offset, gestureZoom: Float, size: IntSize) {
    val newScale = (scale.value * gestureZoom).coerceIn(1f, MAX_SCALE)
    val fromCenter = centroid - center(size)
    val newOffset = fromCenter - (fromCenter - offset.value) * (newScale / scale.value) + pan

    scope.launch {
      scale.snapTo(newScale)
      offset.snapTo(clamp(newOffset, newScale, size))
    }
  }

  private fun center(size: IntSize): Offset = Offset(size.width / 2f, size.height / 2f)

  /** Stops the avatar being dragged so far that its edge comes away from the edge of where it rests. */
  private fun clamp(offset: Offset, scale: Float, size: IntSize): Offset {
    val maxX = size.width * (scale - 1f) / 2f
    val maxY = size.height * (scale - 1f) / 2f
    return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
  }
}

@DayNightPreviews
@PreviewWrapper(SignalPreviewWrapper::class)
@Composable
private fun AvatarPreviewScreenPreview() {
  AvatarPreviewScreen(
    title = "Peter Parker",
    toolbarVisible = true,
    onEvent = {},
    avatar = { modifier -> Box(modifier = modifier.background(MaterialTheme.colorScheme.primaryContainer)) }
  )
}

@DayNightPreviews
@PreviewWrapper(SignalPreviewWrapper::class)
@Composable
private fun AvatarPreviewScreenNoToolbarPreview() {
  AvatarPreviewScreen(
    title = "Peter Parker",
    toolbarVisible = false,
    onEvent = {},
    avatar = { modifier -> Box(modifier = modifier.background(MaterialTheme.colorScheme.primaryContainer)) }
  )
}
