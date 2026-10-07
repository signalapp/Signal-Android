/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit.video.trim

import androidx.compose.ui.unit.Density
import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Test

class VideoTrimBarGeometryTest {

  private val geometry = VideoTrimBarGeometry(width = 380f, density = Density(1f))

  @Test
  fun `the strip is inset from both ends of the bar`() {
    assertThat(geometry.strip.left).isEqualTo(20f)
    assertThat(geometry.strip.right).isEqualTo(360f)
    assertThat(geometry.strip.top).isEqualTo(8f)
    assertThat(geometry.strip.height).isEqualTo(40f)
  }

  @Test
  fun `a time maps onto the strip and back`() {
    val x = geometry.xFor(2_500_000, DURATION_US)

    assertThat(x).isEqualTo(105f)
    assertThat(geometry.positionFor(x, DURATION_US)).isEqualTo(2_500_000L)
  }

  @Test
  fun `positions beyond the strip clamp to its ends`() {
    assertThat(geometry.positionFor(0f, DURATION_US)).isEqualTo(0L)
    assertThat(geometry.positionFor(380f, DURATION_US)).isEqualTo(DURATION_US)
  }

  @Test
  fun `a drag distance maps to a span of time`() {
    assertThat(geometry.durationFor(34f, DURATION_US)).isEqualTo(1_000_000L)
    assertThat(geometry.durationFor(-34f, DURATION_US)).isEqualTo(-1_000_000L)
  }

  @Test
  fun `the handles sit outside the selection`() {
    assertThat(geometry.startHandleCenter(20f)).isEqualTo(10f)
    assertThat(geometry.endHandleCenter(360f)).isEqualTo(370f)
  }

  @Test
  fun `a press within reach of a handle grabs it, anything else grabs the playhead`() {
    assertThat(geometry.hitTest(10f, 0, DURATION_US, DURATION_US)).isEqualTo(TrimDragTarget.START)
    assertThat(geometry.hitTest(33f, 0, DURATION_US, DURATION_US)).isEqualTo(TrimDragTarget.START)
    assertThat(geometry.hitTest(370f, 0, DURATION_US, DURATION_US)).isEqualTo(TrimDragTarget.END)
    assertThat(geometry.hitTest(190f, 0, DURATION_US, DURATION_US)).isEqualTo(TrimDragTarget.PLAYHEAD)
  }

  @Test
  fun `when the handles are close, the nearer one is grabbed`() {
    val startUs = 5_000_000L
    val endUs = 5_500_000L
    val startHandle = geometry.startHandleCenter(geometry.xFor(startUs, DURATION_US))
    val endHandle = geometry.endHandleCenter(geometry.xFor(endUs, DURATION_US))

    assertThat(geometry.hitTest(startHandle, startUs, endUs, DURATION_US)).isEqualTo(TrimDragTarget.START)
    assertThat(geometry.hitTest(endHandle, startUs, endUs, DURATION_US)).isEqualTo(TrimDragTarget.END)
  }

  private companion object {
    const val DURATION_US = 10_000_000L
  }
}
