/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit

import android.app.Application
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.TestMonotonicFrameClock
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.signal.imageeditor.core.model.EditorModel
import org.signal.imageeditor.core.stickers.AnalogClockStickerRenderer

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
// The editor hierarchy builds an inverse-fill Path for the crop mask, which the legacy graphics shadows cannot do.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageControllerStickerTest {

  private val controller = ImageController(EditorModel.create(0))

  @Test
  fun `Given the editor at rest, when a sticker is inserted, then it is left selected and back is the editor's`() {
    controller.enterStickerMode()

    controller.insertSticker(AnalogClockStickerRenderer(0L))

    assertThat(controller.mode).isEqualTo(ImageController.Mode.NONE)
    assertThat(controller.selectedElement).isNotNull()
    assertThat(controller.canHandleBack).isTrue()
  }

  @OptIn(ExperimentalTestApi::class)
  @Test
  fun `Given a freshly inserted sticker, when back is pressed once, then it is deselected`() = runTest {
    withContext(TestMonotonicFrameClock(this)) {
      controller.enterStickerMode()
      controller.insertSticker(AnalogClockStickerRenderer(0L))

      controller.onBackPressed()

      assertThat(controller.selectedElement).isNull()
      assertThat(controller.canHandleBack).isFalse()
    }
  }
}
