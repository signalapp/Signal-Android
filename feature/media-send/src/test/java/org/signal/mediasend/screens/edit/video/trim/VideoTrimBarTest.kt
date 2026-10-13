/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit.video.trim

import android.app.Application
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEmpty
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.ui.CoreUiDependenciesRule

/**
 * Covers how the bar turns touches into events: which part a drag grabs, that every drag is closed out however it ends,
 * and that a tap or a bar with nothing loaded yet reports nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class VideoTrimBarTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  private val events = mutableListOf<VideoTrimBarEvents>()

  private val gestureEvents: List<VideoTrimBarEvents>
    get() = events.filterNot { it is VideoTrimBarEvents.StripMeasured }

  @Test
  fun `when the bar is laid out, then the strip's size is reported`() {
    setContent(LOADED)

    val geometry = geometry()
    assertThat(events.filterIsInstance<VideoTrimBarEvents.StripMeasured>().last()).isEqualTo(
      VideoTrimBarEvents.StripMeasured(geometry.strip.width.toInt(), geometry.strip.height.toInt())
    )
  }

  @Test
  fun `when the end handle is dragged, then the drag starts, moves it, and ends`() {
    setContent(LOADED)
    val endHandle = geometry().endHandleCenter(geometry().strip.right)

    composeTestRule.onNodeWithTag(TAG).performTouchInput {
      down(Offset(endHandle, centerY))
      moveBy(Offset(-100f, 0f))
      up()
    }

    assertThat(gestureEvents.first()).isEqualTo(VideoTrimBarEvents.DragStarted(TrimDragTarget.END))
    assertThat(gestureEvents.filterIsInstance<VideoTrimBarEvents.HandleDragged>()).isNotEmpty()
    assertThat(gestureEvents.filterIsInstance<VideoTrimBarEvents.HandleDragged>().last()).isEqualTo(
      VideoTrimBarEvents.HandleDragged(TrimDragTarget.END, DURATION_US + geometry().durationFor(-100f, DURATION_US))
    )
    assertThat(gestureEvents.last()).isEqualTo(VideoTrimBarEvents.DragEnded)
  }

  @Test
  fun `when the strip is dragged, then the playhead is scrubbed`() {
    setContent(LOADED)
    val middle = geometry().strip.center.x

    composeTestRule.onNodeWithTag(TAG).performTouchInput {
      down(Offset(middle, centerY))
      moveBy(Offset(50f, 0f))
      up()
    }

    assertThat(gestureEvents.first()).isEqualTo(VideoTrimBarEvents.DragStarted(TrimDragTarget.PLAYHEAD))
    assertThat(gestureEvents.filterIsInstance<VideoTrimBarEvents.PlayheadDragged>().last()).isEqualTo(
      VideoTrimBarEvents.PlayheadDragged(geometry().positionFor(middle + 50f, DURATION_US))
    )
    assertThat(gestureEvents.last()).isEqualTo(VideoTrimBarEvents.DragEnded)
  }

  @Test
  fun `when a drag is cancelled, then it is still ended`() {
    setContent(LOADED)

    composeTestRule.onNodeWithTag(TAG).performTouchInput {
      down(Offset(geometry().strip.center.x, centerY))
      moveBy(Offset(50f, 0f))
      cancel()
    }

    assertThat(gestureEvents.first()).isInstanceOf<VideoTrimBarEvents.DragStarted>()
    assertThat(gestureEvents.last()).isEqualTo(VideoTrimBarEvents.DragEnded)
  }

  @Test
  fun `when the strip is tapped, then the playhead is moved there`() {
    setContent(LOADED)
    val middle = geometry().strip.center.x

    composeTestRule.onNodeWithTag(TAG).performTouchInput {
      down(Offset(middle, centerY))
      up()
    }

    assertThat(gestureEvents).containsExactly(VideoTrimBarEvents.PlayheadTapped(geometry().positionFor(middle, DURATION_US)))
  }

  @Test
  fun `when a press is cancelled before it moves, then nothing is reported`() {
    setContent(LOADED)

    composeTestRule.onNodeWithTag(TAG).performTouchInput {
      down(Offset(geometry().strip.center.x, centerY))
      cancel()
    }

    assertThat(gestureEvents).isEmpty()
  }

  @Test
  fun `when the bar is tapped outside the strip, then nothing is reported`() {
    setContent(LOADED)

    composeTestRule.onNodeWithTag(TAG).performTouchInput {
      down(Offset(2f, centerY))
      up()
    }

    assertThat(gestureEvents).isEmpty()
  }

  @Test
  fun `Given the duration is unknown, when the bar is dragged, then nothing is reported`() {
    setContent(VideoTrimBarState())

    composeTestRule.onNodeWithTag(TAG).performTouchInput {
      down(Offset(geometry().strip.center.x, centerY))
      moveBy(Offset(50f, 0f))
      up()
    }

    assertThat(gestureEvents).isEmpty()
  }

  @Test
  fun `Given a trimmed selection, when a press lands beside a handle, then that handle is grabbed`() {
    setContent(LOADED.copy(startUs = 2_000_000, endUs = 6_000_000))
    val geometry = geometry()
    val startHandle = geometry.startHandleCenter(geometry.xFor(2_000_000, DURATION_US))

    composeTestRule.onNodeWithTag(TAG).performTouchInput {
      down(Offset(startHandle + 12f, centerY))
      moveBy(Offset(30f, 0f))
      up()
    }

    assertThat(gestureEvents.filterIsInstance<VideoTrimBarEvents.DragStarted>()).containsExactly(VideoTrimBarEvents.DragStarted(TrimDragTarget.START))
  }

  private fun setContent(state: VideoTrimBarState) {
    composeTestRule.setContent {
      VideoTrimBar(
        state = state,
        onEvent = { events += it },
        modifier = Modifier
          .testTag(TAG)
          .width(BAR_WIDTH)
      )
    }
  }

  private fun geometry(): VideoTrimBarGeometry {
    val density = composeTestRule.density
    return VideoTrimBarGeometry(with(density) { BAR_WIDTH.toPx() }, density)
  }

  private companion object {
    const val TAG = "trim-bar"
    const val DURATION_US = 10_000_000L
    val BAR_WIDTH = 300.dp
    val LOADED = VideoTrimBarState(durationUs = DURATION_US, startUs = 0, endUs = DURATION_US)
  }
}
