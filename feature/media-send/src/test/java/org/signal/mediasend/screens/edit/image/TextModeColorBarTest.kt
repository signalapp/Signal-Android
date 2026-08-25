/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit.image

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
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
import org.signal.mediasend.MediaSendDependenciesRule
import org.signal.mediasend.R
import org.signal.mediasend.screens.edit.ImageController

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h800dp")
class TextModeColorBarTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  @get:Rule
  val mediaSendDependenciesRule = MediaSendDependenciesRule(ApplicationProvider.getApplicationContext())

  @Test
  fun `Given a window barely wider than the color bar, when rendering the bar, then the style toggle is still there to tap`() {
    composeTestRule.setContent {
      SignalTheme {
        Box(modifier = Modifier.fillMaxWidth()) {
          TextModeColorBar(
            imageEditorController = remember { ImageController(EDITOR_MODEL) },
            modifier = Modifier.padding(horizontal = 16.dp),
            orientation = ColorBarOrientation.HORIZONTAL
          )
        }
      }
    }

    composeTestRule
      .onNodeWithContentDescription(toggleContentDescription(), useUnmergedTree = true)
      .assertWidthIsAtLeast(MIN_TOGGLE_ICON_WIDTH)
  }

  private fun toggleContentDescription(): String {
    return ApplicationProvider.getApplicationContext<Application>().getString(R.string.TextModeColorBar__toggle_between_text_styles)
  }

  companion object {
    private val MIN_TOGGLE_ICON_WIDTH = 20.dp
    private val EDITOR_MODEL: EditorModel = mockk(relaxed = true)
  }
}
