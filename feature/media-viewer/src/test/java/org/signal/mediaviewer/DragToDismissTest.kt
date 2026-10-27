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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
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
    setContent(childReleasesAfter = 20.dp)

    composeTestRule.onNodeWithTag(TAG).performTouchInput { swipeDown(startY = centerY, endY = bottom, durationMillis = 1_000) }
    composeTestRule.waitForIdle()

    assertThat(dismissed).isEqualTo(listOf(false))
  }

  @Test
  fun `a drag kept by a child does not dismiss`() {
    setContent(childReleasesAfter = null)

    composeTestRule.onNodeWithTag(TAG).performTouchInput { swipeDown(startY = centerY, endY = bottom, durationMillis = 1_000) }
    composeTestRule.waitForIdle()

    assertThat(dismissed).isEmpty()
  }

  private fun setContent(childReleasesAfter: Dp?) {
    composeTestRule.setContent {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .dragToDismiss(
            state = rememberDragToDismissState(),
            canStart = { true },
            canDismissToSharedElement = { false },
            onDismissed = { dismissed += it }
          )
          .testTag(TAG)
      ) {
        Box(modifier = Modifier.fillMaxSize().claimingChild(childReleasesAfter))
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

  companion object {
    private const val TAG = "drag"
  }
}
