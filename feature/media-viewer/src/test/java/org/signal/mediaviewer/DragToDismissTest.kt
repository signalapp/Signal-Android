/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediaviewer

import android.app.Application
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.cancel
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h640dp-xhdpi")
class DragToDismissTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val dismissed = mutableListOf<Boolean>()

  @Test
  fun `a drag released by a child that claimed it at touch down is picked up`() {
    setContent(child = ClaimingChild.ReleasesAfter(20.dp))

    composeTestRule.onNodeWithTag(TAG).performTouchInput { swipeDown(startY = centerY, endY = bottom, durationMillis = 1_000) }
    composeTestRule.waitForIdle()

    assertThat(dismissed).isEqualTo(listOf(false))
  }

  @Test
  fun `a drag kept by a child does not dismiss`() {
    setContent(child = ClaimingChild.KeepsForever)

    composeTestRule.onNodeWithTag(TAG).performTouchInput { swipeDown(startY = centerY, endY = bottom, durationMillis = 1_000) }
    composeTestRule.waitForIdle()

    assertThat(dismissed).isEmpty()
  }

  @Test
  fun `a horizontal drag is not claimed`() {
    val state = DragToDismissState()
    setContent(state = state)

    composeTestRule.onNodeWithTag(TAG).performTouchInput { swipe(start = center, end = Offset(right, centerY), durationMillis = 1_000) }
    composeTestRule.waitForIdle()

    assertThat(dismissed).isEmpty()
    assertThat(state.offsetX).isEqualTo(0f)
  }

  @Test
  fun `once claimed the media follows the finger in any direction and a far release dismisses`() {
    val state = DragToDismissState()
    setContent(state = state)

    composeTestRule.onNodeWithTag(TAG).performTouchInput {
      down(center)
      moveBy(Offset(0f, 120.dp.toPx()))
      moveBy(Offset(150.dp.toPx(), -120.dp.toPx()))
    }
    composeTestRule.waitForIdle()

    assertThat(state.offsetX).isGreaterThan(0f)

    composeTestRule.onNodeWithTag(TAG).performTouchInput { up() }
    composeTestRule.waitForIdle()

    assertThat(dismissed).isEqualTo(listOf(false))
  }

  @Test
  fun `horizontal movement is followed as soon as the vertical drag is claimed`() {
    val state = DragToDismissState()
    setContent(state = state)

    composeTestRule.onNodeWithTag(TAG).performTouchInput {
      down(center)
      moveBy(Offset(0f, 20.dp.toPx()))
      moveBy(Offset(100.dp.toPx(), 0f))
    }
    composeTestRule.waitForIdle()

    assertThat(state.offsetX).isGreaterThan(0f)
    assertThat(state.offsetY).isGreaterThan(0f)

    composeTestRule.onNodeWithTag(TAG).performTouchInput { cancel() }
  }

  @Test
  fun `the media scales down as it is dragged vertically`() {
    val state = DragToDismissState()
    setContent(state = state)

    composeTestRule.onNodeWithTag(TAG).performTouchInput {
      down(center)
      moveBy(Offset(0f, 120.dp.toPx()))
    }
    composeTestRule.waitForIdle()

    assertThat(state.scale).isLessThan(1f)
    assertThat(state.scale).isGreaterThan(MIN_SCALE)

    composeTestRule.onNodeWithTag(TAG).performTouchInput { cancel() }
  }

  private fun setContent(child: ClaimingChild = ClaimingChild.None, state: DragToDismissState = DragToDismissState()) {
    composeTestRule.setContent {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .dragToDismiss(
            state = state,
            canStart = { true },
            canDismissToSharedElement = { false },
            onDismissed = { dismissed += it }
          )
          .testTag(TAG)
      ) {
        when (child) {
          ClaimingChild.None -> Unit
          ClaimingChild.KeepsForever -> Box(modifier = Modifier.fillMaxSize().claimingChild(releaseAfter = null))
          is ClaimingChild.ReleasesAfter -> Box(modifier = Modifier.fillMaxSize().claimingChild(releaseAfter = child.distance))
        }
      }
    }
  }

  /**
   * Mimics how Compose's view interop consumes every move for a child View that requested
   * disallow-intercept at touch down, until that View releases it after [releaseAfter] of travel.
   */
  private fun Modifier.claimingChild(releaseAfter: Dp?): Modifier {
    return pointerInput(releaseAfter) {
      val releaseDistance = releaseAfter?.toPx()
      awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        down.consume()
        var released = false
        while (true) {
          val change = awaitPointerEvent(PointerEventPass.Initial).changes.first { it.id == down.id }
          if (!change.pressed) {
            break
          }
          if (!released) {
            change.consume()
          }
          if (releaseDistance != null && (change.position - down.position).getDistance() > releaseDistance) {
            released = true
          }
        }
      }
    }
  }

  private sealed interface ClaimingChild {
    data object None : ClaimingChild
    data object KeepsForever : ClaimingChild
    data class ReleasesAfter(val distance: Dp) : ClaimingChild
  }

  companion object {
    private const val TAG = "drag"
  }
}
