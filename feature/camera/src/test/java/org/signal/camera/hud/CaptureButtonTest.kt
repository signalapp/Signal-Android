/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.camera.hud

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.camera.test.TestTags

/**
 * Covers what the capture button makes of a gesture: which of a tap, a hold and a drag to the lock it reports, and that
 * a drag reaching the lock leaves the recording running rather than ending it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CaptureButtonTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val gestures = mutableListOf<String>()
  private val zoomChanges = mutableListOf<Float>()
  private val haptics = mutableListOf<HapticFeedbackType>()
  private var longPressTimeoutMillis = 0L

  /** How near the lock's center takes hold of it: its own radius plus the snap margin beyond that. */
  private var lockSnapPx = 0f

  /** How far back off the lock the finger has to come to release it, which is further out than [lockSnapPx]. */
  private var lockSnapOffPx = 0f

  @Test
  fun `Given a press let go of at once, when it is over, then it was a tap`() {
    setContent()

    composeTestRule.onNodeWithTag(TestTags.CAMERA_HUD_CAPTURE_BUTTON).performTouchInput {
      down(center)
      up()
    }
    composeTestRule.waitForIdle()

    assertThat(gestures).isEqualTo(listOf(TAP))
  }

  @Test
  fun `Given a press held on, when it is let go of, then it was a hold from beginning to end`() {
    setContent()

    press()
    lift()

    assertThat(gestures).isEqualTo(listOf(LONG_PRESS_START, LONG_PRESS_END))
  }

  /** Only a drag that carries the whole way reaches the lock. */
  @Test
  fun `Given a hold dragged part way to the lock, when it is let go of, then the hold ends and nothing is locked`() {
    setContent()

    press()
    dragBy(Offset(x = LOCK_OFFSET.x / 2f, y = 0f))
    lift()

    assertThat(gestures).isEqualTo(listOf(LONG_PRESS_START, LONG_PRESS_END))
  }

  /** Arriving is not taking it: the lock waits for the finger to lift over it. */
  @Test
  fun `Given a hold dragged to the lock, when it arrives, then nothing is locked yet`() {
    setContent()

    press()
    dragBy(LOCK_OFFSET)

    assertThat(gestures).isEqualTo(listOf(LONG_PRESS_START))
  }

  @Test
  fun `Given a hold that has arrived at the lock, when it is let go of, then the lock is taken`() {
    setContent()

    press()
    dragBy(LOCK_OFFSET)
    lift()

    assertThat(gestures).isEqualTo(listOf(LONG_PRESS_START, LOCKED))
  }

  /** One haptic as the finger arrives on the lock, and another as it lifts and takes it. */
  @Test
  fun `Given a hold that reached the lock, when it is let go of, then the snap and the lock are both felt`() {
    setContent()

    press()
    dragBy(LOCK_OFFSET)
    lift()

    assertThat(haptics).isEqualTo(listOf(HapticFeedbackType.SegmentTick, HapticFeedbackType.LongPress))
  }

  /** Only the crossing plays, so the snap fires once however long the finger stays on the lock. */
  @Test
  fun `Given a finger resting on the lock, when it moves about on it, then the snap is felt only once`() {
    setContent()

    press()
    dragBy(LOCK_OFFSET)
    dragBy(Offset(x = 1f, y = 1f))
    dragBy(Offset(x = -1f, y = -1f))

    assertThat(haptics).isEqualTo(listOf(HapticFeedbackType.SegmentTick))
  }

  @Test
  fun `Given a hold that never reached the lock, when it is let go of, then nothing is felt`() {
    setContent()

    press()
    lift()

    assertThat(haptics).isEmpty()
  }

  /**
   * Once the lock has taken hold it keeps it until the finger is clearly off, so a thumb wavering on the boundary does
   * not turn it on and off.
   */
  @Test
  fun `Given a hold that reached the lock, when it drifts just back off it, then the lock still has it`() {
    setContent()

    press()
    dragBy(LOCK_OFFSET)
    dragBy(Offset(x = (lockSnapPx + lockSnapOffPx) / 2f, y = 0f))
    lift()

    assertThat(gestures).isEqualTo(listOf(LONG_PRESS_START, LOCKED))
  }

  /** Anywhere on the lock is somewhere to let go, not only its exact center. */
  @Test
  fun `Given a hold dragged onto the near edge of the lock, when it is let go of, then the lock is taken`() {
    setContent()

    press()
    dragBy(Offset(x = LOCK_OFFSET.x + lockSnapPx, y = 0f))
    lift()

    assertThat(gestures).isEqualTo(listOf(LONG_PRESS_START, LOCKED))
  }

  /** A finger that crosses the lock and carries on is headed elsewhere, so it leaves the recording as it was. */
  @Test
  fun `Given a hold dragged across the lock and off it, when it is let go of, then nothing is locked`() {
    setContent()

    press()
    dragBy(LOCK_OFFSET)
    dragBy(LOCK_OFFSET)
    lift()

    assertThat(gestures).isEqualTo(listOf(LONG_PRESS_START, LONG_PRESS_END))
  }

  /** Lifting after locking must not end the recording the lock just took over. */
  @Test
  fun `Given a hold that reached the lock, when it is let go of, then the hold does not end`() {
    setContent()

    press()
    dragBy(LOCK_OFFSET)
    lift()

    assertThat(gestures).isEqualTo(listOf(LONG_PRESS_START, LOCKED))
  }

  /** Only travel toward the lock counts, so a drag the other way gets no closer. */
  @Test
  fun `Given a hold dragged away from the lock, when it is let go of, then nothing is locked`() {
    setContent()

    press()
    dragBy(Offset(x = -LOCK_OFFSET.x, y = 0f))
    lift()

    assertThat(gestures).isEqualTo(listOf(LONG_PRESS_START, LONG_PRESS_END))
  }

  @Test
  fun `Given no lock on offer, when a hold is dragged the whole way across, then nothing is locked`() {
    setContent(lockOffset = Offset.Zero)

    press()
    dragBy(LOCK_OFFSET)
    lift()

    assertThat(gestures).isEqualTo(listOf(LONG_PRESS_START, LONG_PRESS_END))
  }

  /** A drag that has carried past the slop toward the lock is headed there rather than zooming. */
  @Test
  fun `Given a hold dragged toward the lock, when it moves, then the zoom is left alone`() {
    setContent()

    press()
    dragBy(Offset(x = LOCK_OFFSET.x / 2f, y = -400f))
    lift()

    assertThat(zoomChanges).isEmpty()
  }

  @Test
  fun `Given a hold dragged across the way to the lock, when it moves, then the zoom follows it`() {
    setContent()

    press()
    dragBy(Offset(x = 0f, y = -400f))
    lift()

    assertThat(zoomChanges.any { it > 0f }).isTrue()
  }

  /** A drag that has only drifted a pixel toward the lock has not set off for it, so the zoom keeps the drag. */
  @Test
  fun `Given a hold dragged up with a drift toward the lock, when it moves, then the zoom follows it`() {
    setContent()

    press()
    dragBy(Offset(x = -1f, y = -400f))
    lift()

    assertThat(zoomChanges.any { it > 0f }).isTrue()
  }

  private fun press() {
    composeTestRule.onNodeWithTag(TestTags.CAMERA_HUD_CAPTURE_BUTTON).performTouchInput { down(center) }
    composeTestRule.mainClock.advanceTimeBy(longPressTimeoutMillis + 100L)
    composeTestRule.waitForIdle()
  }

  private fun dragBy(offset: Offset) {
    composeTestRule.onNodeWithTag(TestTags.CAMERA_HUD_CAPTURE_BUTTON).performTouchInput { moveBy(offset) }
    composeTestRule.waitForIdle()
  }

  private fun lift() {
    composeTestRule.onNodeWithTag(TestTags.CAMERA_HUD_CAPTURE_BUTTON).performTouchInput { up() }
    composeTestRule.waitForIdle()
  }

  private fun setContent(
    state: CaptureButtonState = CaptureButtonState.PHOTO,
    lockOffset: Offset = LOCK_OFFSET
  ) {
    composeTestRule.setContent {
      longPressTimeoutMillis = LocalViewConfiguration.current.longPressTimeoutMillis
      with(LocalDensity.current) {
        lockSnapPx = CaptureButtonDimensions.LockDraggableSize.toPx() / 2f + CaptureButtonDimensions.LockSnapMargin.toPx()
        lockSnapOffPx = lockSnapPx + CaptureButtonDimensions.LockSnapRelease.toPx()
      }

      val recordingHaptics = remember {
        object : HapticFeedback {
          override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            haptics += hapticFeedbackType
          }
        }
      }

      CompositionLocalProvider(LocalHapticFeedback provides recordingHaptics) {
        CaptureButton(
          state = state,
          onTap = { gestures += TAP },
          onLongPressStart = { gestures += LONG_PRESS_START },
          onLongPressEnd = { gestures += LONG_PRESS_END },
          onZoomChange = { zoomChanges += it },
          onLock = { gestures += LOCKED },
          lockOffset = lockOffset
        )
      }
    }

    composeTestRule.waitForIdle()
  }

  companion object {
    private const val TAP = "tap"
    private const val LONG_PRESS_START = "long_press_start"
    private const val LONG_PRESS_END = "long_press_end"
    private const val LOCKED = "locked"

    /** A lock off to the start side, which is where a portrait phone puts it. */
    private val LOCK_OFFSET = Offset(x = -300f, y = 0f)
  }
}
