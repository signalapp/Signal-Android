/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit.video.trim

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.SignalPreviewWrapper
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Canvas as GraphicsCanvas

private val DimColor = Color.Black.copy(alpha = 0.72f)

/**
 * Thumbnail strip of a video with a pair of trim handles and a playhead. Stateless: it draws [state] and reports what
 * the user does through [onEvent], in the video's time rather than in pixels. Pair it with a [VideoTrimBarPresenter].
 */
@Composable
fun VideoTrimBar(
  state: VideoTrimBarState,
  onEvent: (VideoTrimBarEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  val density = LocalDensity.current
  val currentState by rememberUpdatedState(state)
  val currentOnEvent by rememberUpdatedState(onEvent)

  val colors = VideoTrimBarColors(
    background = MaterialTheme.colorScheme.surfaceVariant,
    selection = MaterialTheme.colorScheme.primary,
    handle = MaterialTheme.colorScheme.secondary,
    trimmedHandle = MaterialTheme.colorScheme.onPrimary,
    playhead = MaterialTheme.colorScheme.secondary,
    trimmedPlayhead = MaterialTheme.colorScheme.onSurface
  )

  Canvas(
    modifier = modifier
      .fillMaxWidth()
      .height(VideoTrimBarGeometry.BarHeight)
      // The handles sit at the screen's edges, where the system back gesture would otherwise take their drags.
      .systemGestureExclusion()
      .onSizeChanged { size ->
        val geometry = VideoTrimBarGeometry(size.width.toFloat(), density)
        currentOnEvent(VideoTrimBarEvents.StripMeasured(geometry.strip.width.roundToInt(), geometry.strip.height.roundToInt()))
      }
      .pointerInput(Unit) {
        awaitEachGesture {
          val down = awaitFirstDown(requireUnconsumed = false)
          val snapshot = currentState
          val durationUs = snapshot.durationUs ?: return@awaitEachGesture

          val geometry = VideoTrimBarGeometry(size.width.toFloat(), this)
          val target = geometry.hitTest(down.position.x, snapshot.startUs, snapshot.endUs, durationUs)
          val downX = down.position.x

          fun report(x: Float) {
            val event = when (target) {
              TrimDragTarget.START -> VideoTrimBarEvents.HandleDragged(target, snapshot.startUs + geometry.durationFor(x - downX, durationUs))
              TrimDragTarget.END -> VideoTrimBarEvents.HandleDragged(target, snapshot.endUs + geometry.durationFor(x - downX, durationUs))
              TrimDragTarget.PLAYHEAD -> VideoTrimBarEvents.PlayheadDragged(geometry.positionFor(x, durationUs))
            }
            currentOnEvent(event)
          }

          val slop = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
          if (slop == null) {
            val release = currentEvent.changes.firstOrNull { it.id == down.id }
            if (release != null && release.changedToUp() && !release.isConsumed && down.position.x in geometry.strip.left..geometry.strip.right) {
              release.consume()
              currentOnEvent(VideoTrimBarEvents.PlayheadTapped(geometry.positionFor(down.position.x, durationUs)))
            }
            return@awaitEachGesture
          }

          currentOnEvent(VideoTrimBarEvents.DragStarted(target))
          try {
            report(slop.position.x)
            horizontalDrag(slop.id) { change ->
              change.consume()
              report(change.position.x)
            }
          } finally {
            currentOnEvent(VideoTrimBarEvents.DragEnded)
          }
        }
      }
  ) {
    drawTrimBar(state, VideoTrimBarGeometry(size.width, this), colors)
  }
}

private data class VideoTrimBarColors(
  val background: Color,
  val selection: Color,
  val handle: Color,
  val trimmedHandle: Color,
  val playhead: Color,
  val trimmedPlayhead: Color
)

private fun DrawScope.drawTrimBar(state: VideoTrimBarState, geometry: VideoTrimBarGeometry, colors: VideoTrimBarColors) {
  val strip = geometry.strip
  val stripPath = Path().apply { addRoundRect(RoundRect(strip, CornerRadius(geometry.stripRadius))) }

  drawRoundRect(
    color = colors.background,
    topLeft = Offset(0f, geometry.pillTop),
    size = Size(size.width, geometry.pillHeight),
    cornerRadius = CornerRadius(geometry.pillRadius)
  )

  clipPath(stripPath) {
    drawThumbnails(state.thumbnails, strip)
  }

  val durationUs = state.durationUs ?: 0L
  val startX = if (durationUs > 0) geometry.xFor(state.startUs, durationUs) else strip.left
  val endX = if (durationUs > 0) geometry.xFor(state.endUs, durationUs) else strip.right

  if (state.isTrimmed) {
    clipPath(stripPath) {
      drawRect(DimColor, topLeft = Offset(strip.left, strip.top), size = Size(startX - strip.left, strip.height))
      drawRect(DimColor, topLeft = Offset(endX, strip.top), size = Size(strip.right - endX, strip.height))
    }

    drawRoundRect(
      color = colors.selection,
      topLeft = Offset(startX - geometry.stripInset, geometry.pillTop),
      size = Size(endX - startX + geometry.stripInset * 2, geometry.pillHeight),
      cornerRadius = CornerRadius(geometry.pillRadius)
    )

    val selection = Rect(startX, strip.top, endX, strip.bottom)
    val selectionPath = Path().apply { addRoundRect(RoundRect(selection, CornerRadius(geometry.stripRadius))) }
    clipPath(selectionPath) {
      drawRect(colors.background, topLeft = selection.topLeft, size = selection.size)
      drawThumbnails(state.thumbnails, strip)
    }
  }

  val handleColor = if (state.isTrimmed) colors.trimmedHandle else colors.handle
  drawHandle(geometry.startHandleCenter(startX), geometry, handleColor)
  drawHandle(geometry.endHandleCenter(endX), geometry, handleColor)

  val isHandleDragging = state.activeDrag == TrimDragTarget.START || state.activeDrag == TrimDragTarget.END
  if (durationUs > 0 && !isHandleDragging && state.playheadUs in state.startUs..state.endUs) {
    val playheadX = geometry.xFor(state.playheadUs, durationUs)
    val left = (playheadX - geometry.playheadWidth / 2).coerceIn(startX, (endX - geometry.playheadWidth).coerceAtLeast(startX))
    drawRoundRect(
      color = if (state.isTrimmed) colors.trimmedPlayhead else colors.playhead,
      topLeft = Offset(left, 0f),
      size = Size(geometry.playheadWidth, size.height),
      cornerRadius = CornerRadius(geometry.playheadRadius)
    )
  }
}

/** Tiles the strip with square, centre-cropped thumbnails, the last cut off by the strip's end. */
private fun DrawScope.drawThumbnails(thumbnails: List<ImageBitmap?>, strip: Rect) {
  val tileSize = strip.height.roundToInt()
  if (tileSize <= 0) {
    return
  }

  thumbnails.forEachIndexed { index, thumbnail ->
    if (thumbnail == null) {
      return@forEachIndexed
    }

    val side = min(thumbnail.width, thumbnail.height)
    drawImage(
      image = thumbnail,
      srcOffset = IntOffset((thumbnail.width - side) / 2, (thumbnail.height - side) / 2),
      srcSize = IntSize(side, side),
      dstOffset = IntOffset((strip.left + index * strip.height).roundToInt(), strip.top.roundToInt()),
      dstSize = IntSize(tileSize, tileSize)
    )
  }
}

private fun DrawScope.drawHandle(centerX: Float, geometry: VideoTrimBarGeometry, color: Color) {
  drawRoundRect(
    color = color,
    topLeft = Offset(centerX - geometry.handleWidth / 2, geometry.handleTop),
    size = Size(geometry.handleWidth, geometry.handleHeight),
    cornerRadius = CornerRadius(geometry.handleRadius)
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun VideoTrimBarUntrimmedPreview() {
  VideoTrimBar(
    state = VideoTrimBarState(
      durationUs = 10_000_000,
      startUs = 0,
      endUs = 10_000_000,
      playheadUs = 0,
      thumbnails = rememberPreviewThumbnails()
    ),
    onEvent = {}
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun VideoTrimBarTrimmedPreview() {
  VideoTrimBar(
    state = VideoTrimBarState(
      durationUs = 10_000_000,
      startUs = 0,
      endUs = 6_400_000,
      playheadUs = 0,
      thumbnails = rememberPreviewThumbnails()
    ),
    onEvent = {}
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun VideoTrimBarLoadingPreview() {
  VideoTrimBar(
    state = VideoTrimBarState(),
    onEvent = {}
  )
}

@Composable
private fun rememberPreviewThumbnails(): List<ImageBitmap?> {
  return remember {
    List(10) { index ->
      ImageBitmap(64, 64).also { bitmap ->
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, GraphicsCanvas(bitmap), Size(64f, 64f)) {
          drawRect(
            Brush.verticalGradient(
              listOf(
                Color.hsv(hue = 40f + index * 12f, saturation = 0.5f, value = 0.8f),
                Color.hsv(hue = 110f, saturation = 0.4f, value = 0.3f)
              )
            )
          )
        }
      }
    }
  }
}
