/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.camera.hud

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.camera.test.TestTags

/** Covers the button that takes the gallery's place once a recording is running unheld. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RecordingActionButtonsTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private var clicks = 0

  @Test
  fun `Given a running recording, when the pause is clicked, then the pause is asked for`() {
    setPauseButtonContent(isPaused = false)

    composeTestRule.onNodeWithTag(TestTags.CAMERA_HUD_PAUSE_BUTTON).performClick()

    assertThat(clicks).isEqualTo(1)
  }

  /** The same button offers to resume once it has been used, so it raises the same event either way. */
  @Test
  fun `Given a paused recording, when the button is clicked, then carrying on is asked for`() {
    setPauseButtonContent(isPaused = true)

    composeTestRule.onNodeWithTag(TestTags.CAMERA_HUD_PAUSE_BUTTON).performClick()

    assertThat(clicks).isEqualTo(1)
  }

  private fun setPauseButtonContent(isPaused: Boolean) {
    composeTestRule.setContent {
      RecordingPauseButton(
        isPaused = isPaused,
        onClick = { clicks++ }
      )
    }

    composeTestRule.waitForIdle()
  }
}
