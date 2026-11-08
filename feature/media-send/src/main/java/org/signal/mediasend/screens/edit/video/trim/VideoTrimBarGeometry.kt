/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit.video.trim

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToLong

/** Where everything in [VideoTrimBar] sits, and the mapping between the strip's pixels and the video's time. */
internal class VideoTrimBarGeometry(
  val width: Float,
  private val density: Density
) {

  companion object {
    val BarHeight = 56.dp
    val PillTop = 4.dp
    val PillHeight = 48.dp
    val PillRadius = 16.dp
    val StripInset = 20.dp
    val StripTop = 8.dp
    val StripHeight = 40.dp
    val StripRadius = 8.dp
    val HandleWidth = 4.dp
    val HandleHeight = 28.dp
    val HandleTop = 14.dp
    val HandleRadius = 2.dp

    /** How far each handle's centre sits outside the end of the selection it controls. */
    val HandleOffset = 10.dp
    val PlayheadWidth = 6.dp
    val PlayheadRadius = 3.dp
    val TouchRadius = 24.dp
  }

  private fun px(value: Dp): Float = with(density) { value.toPx() }

  val pillTop: Float = px(PillTop)
  val pillHeight: Float = px(PillHeight)
  val pillRadius: Float = px(PillRadius)
  val stripInset: Float = px(StripInset)
  val stripRadius: Float = px(StripRadius)
  val handleWidth: Float = px(HandleWidth)
  val handleHeight: Float = px(HandleHeight)
  val handleTop: Float = px(HandleTop)
  val handleRadius: Float = px(HandleRadius)
  val playheadWidth: Float = px(PlayheadWidth)
  val playheadRadius: Float = px(PlayheadRadius)

  private val handleOffset: Float = px(HandleOffset)
  private val touchRadius: Float = px(TouchRadius)

  val strip: Rect = Rect(
    left = stripInset,
    top = px(StripTop),
    right = (width - stripInset).coerceAtLeast(stripInset),
    bottom = px(StripTop) + px(StripHeight)
  )

  fun xFor(positionUs: Long, durationUs: Long): Float {
    if (durationUs <= 0) {
      return strip.left
    }
    return strip.left + strip.width * (positionUs.toFloat() / durationUs).coerceIn(0f, 1f)
  }

  fun positionFor(x: Float, durationUs: Long): Long {
    if (strip.width <= 0f) {
      return 0
    }
    return (((x - strip.left) / strip.width).coerceIn(0f, 1f) * durationUs).roundToLong()
  }

  /** How much time a horizontal drag of [distance] pixels covers. */
  fun durationFor(distance: Float, durationUs: Long): Long {
    if (strip.width <= 0f) {
      return 0
    }
    return (distance / strip.width * durationUs).roundToLong()
  }

  fun startHandleCenter(startX: Float): Float = startX - handleOffset

  fun endHandleCenter(endX: Float): Float = endX + handleOffset

  /** Whichever handle is nearest [x], if it is within reach, otherwise the playhead. */
  fun hitTest(x: Float, startUs: Long, endUs: Long, durationUs: Long): TrimDragTarget {
    val startDistance = abs(x - startHandleCenter(xFor(startUs, durationUs)))
    val endDistance = abs(x - endHandleCenter(xFor(endUs, durationUs)))

    return when {
      startDistance <= endDistance && startDistance <= touchRadius -> TrimDragTarget.START
      endDistance < startDistance && endDistance <= touchRadius -> TrimDragTarget.END
      else -> TrimDragTarget.PLAYHEAD
    }
  }
}
