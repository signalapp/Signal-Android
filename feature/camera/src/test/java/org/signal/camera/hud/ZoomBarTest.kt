/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.camera.hud

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.camera.CameraDisplay

/**
 * Covers what the zoom bar puts on screen for a given lens and window, which of its levels reads as the one showing, and
 * what a tap on one of them asks for.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ZoomBarTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val picked = mutableListOf<ZoomBarLevel>()

  @Test
  fun `Given a lens that reaches every level, when displayed, then each one is on the bar`() {
    setContent()

    composeTestRule.onNodeWithText(".5").assertIsDisplayed()
    composeTestRule.onNodeWithText("1").assertIsDisplayed()
    composeTestRule.onNodeWithText("2").assertIsDisplayed()
    composeTestRule.onNodeWithText("5").assertIsDisplayed()
  }

  @Test
  fun `Given the camera at a level, when displayed, then that level is the one showing`() {
    setContent(zoomRatio = 2f)

    composeTestRule.onNodeWithText("2").assertIsSelected()
    composeTestRule.onNodeWithText("1").assertIsNotSelected()
  }

  /** A ratio between two levels, which is where a pinch tends to leave the camera. */
  @Test
  fun `Given the camera between two levels, when displayed, then none of them is showing`() {
    setContent(zoomRatio = 3.4f)

    composeTestRule.onNodeWithText("2").assertIsNotSelected()
    composeTestRule.onNodeWithText("5").assertIsNotSelected()
  }

  @Test
  fun `when a level is picked, then it is asked for`() {
    setContent()

    composeTestRule.onNodeWithText("5").performClick()

    assertThat(picked).containsExactly(ZoomBarLevel.FIVE)
  }

  @Test
  fun `Given a lens that stops short, when displayed, then the levels past it are not on the bar`() {
    setContent(zoomRange = 1f..3f)

    composeTestRule.onNodeWithText("1").assertIsDisplayed()
    composeTestRule.onNodeWithText("2").assertIsDisplayed()
    composeTestRule.onNodeWithText(".5").assertDoesNotExist()
    composeTestRule.onNodeWithText("5").assertDoesNotExist()
  }

  /** The shortest window is filled by the viewfinder, leaving the bar nowhere to sit. */
  @Test
  fun `Given a window with no room, when displayed, then no bar is offered`() {
    setContent(cameraDisplay = CameraDisplay.DISPLAY_16_9)

    composeTestRule.onNodeWithText("1").assertDoesNotExist()
    composeTestRule.onNodeWithText("2").assertDoesNotExist()
  }

  @Test
  fun `Given a lens that does not zoom, when displayed, then no bar is offered`() {
    setContent(zoomRange = 1f..1f)

    composeTestRule.onNodeWithText("1").assertDoesNotExist()
  }

  /** A bar that has faded out cannot be used, however much of it is still on screen. */
  @Test
  fun `Given the bar is not showing, when a level is picked, then nothing is asked for`() {
    setContent(visible = false)

    composeTestRule.onNodeWithText("2").assertIsNotEnabled()
    composeTestRule.onNodeWithText("2").performClick()

    assertThat(picked).isEmpty()
  }

  private fun setContent(
    zoomRatio: Float = 1f,
    zoomRange: ClosedFloatingPointRange<Float> = 0.5f..10f,
    cameraDisplay: CameraDisplay = CameraDisplay.DISPLAY_20_9,
    visible: Boolean = true
  ) {
    composeTestRule.setContent {
      ZoomBar(
        zoomRatio = zoomRatio,
        zoomRange = zoomRange,
        cameraDisplay = cameraDisplay,
        onZoomLevelClick = { picked += it },
        visible = visible
      )
    }

    composeTestRule.waitForIdle()
  }
}
