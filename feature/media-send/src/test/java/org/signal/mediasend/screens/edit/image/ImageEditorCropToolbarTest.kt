/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit.image

import android.app.Application
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.ui.CoreUiDependenciesRule
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.imageeditor.core.model.EditorModel
import org.signal.mediasend.EditorState
import org.signal.mediasend.MediaSendDependenciesRule
import org.signal.mediasend.screens.edit.ImageController
import org.signal.mediasend.screens.edit.MediaEditState

/**
 * The aspect ratio toggle is an icon-only button whose icon is the only thing that says whether the crop is locked, so the
 * content description has to carry that state for TalkBack.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ImageEditorCropToolbarTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  @get:Rule
  val mediaSendDependenciesRule = MediaSendDependenciesRule(ApplicationProvider.getApplicationContext())

  @Test
  fun `Given an unlocked crop, when rendering the toolbar, then the aspect ratio toggle announces that it is unlocked`() {
    setContent()

    composeTestRule.onNodeWithContentDescription(UNLOCKED).assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription(LOCKED).assertDoesNotExist()
  }

  @Test
  fun `Given an unlocked crop, when the aspect ratio toggle is tapped, then it announces that it is locked`() {
    setContent()

    composeTestRule.onNodeWithContentDescription(UNLOCKED).performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithContentDescription(LOCKED).assertIsDisplayed()
  }

  private fun setContent() {
    composeTestRule.setContent {
      SignalTheme {
        val controller = remember {
          ImageController(EDITOR_MODEL).apply { enterCropMode() }
        }

        ImageEditorToolbar(
          imageEditorController = controller,
          state = MediaEditState(),
          editorState = EditorState.Image(EDITOR_MODEL),
          onEvent = {}
        )
      }
    }
  }

  companion object {
    private const val LOCKED = "Aspect ratio locked"
    private const val UNLOCKED = "Aspect ratio unlocked"

    /** The editor model is never read by the toolbar, and a real one cannot be built under Robolectric's legacy graphics. */
    private val EDITOR_MODEL: EditorModel = mockk(relaxed = true)
  }
}
