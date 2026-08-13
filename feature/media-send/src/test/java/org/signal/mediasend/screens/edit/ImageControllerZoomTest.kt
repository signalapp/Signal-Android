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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.signal.imageeditor.core.model.EditorElement
import org.signal.imageeditor.core.model.EditorModel

/**
 * Zoom is a mode rather than a transform sitting on top of one: it opens on the first pinch away from the fit scale and
 * closes again whenever the canvas goes back to it, taking the selection and the pager lock with it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
// The editor hierarchy builds an inverse-fill Path for the crop mask, which the legacy graphics shadows cannot do.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageControllerZoomTest {

  private val controller = ImageController(EditorModel.create(0))

  @Test
  fun `Given the editor at rest, when the canvas is pinched, then it enters zoom mode`() {
    controller.pinch(scaleFactor = 2f)

    assertThat(controller.mode).isEqualTo(ImageController.Mode.ZOOM)
    assertThat(controller.imageEditorState.isZoomed).isTrue()
    assertThat(controller.isUserInEdit).isFalse()
  }

  @Test
  fun `Given a zoomed canvas, when it is pinched back to its fit, then it returns to rest`() {
    controller.pinch(scaleFactor = 2f)

    controller.pinch(scaleFactor = 0.5f)

    assertThat(controller.mode).isEqualTo(ImageController.Mode.NONE)
    assertThat(controller.imageEditorState.isZoomed).isFalse()
  }

  @Test
  fun `Given a zoomed canvas, when back is pressed, then the zoom is dropped and the editor is back at rest`() = runZoomTest {
    controller.pinch(scaleFactor = 2f)

    controller.onBackPressed()

    assertThat(controller.mode).isEqualTo(ImageController.Mode.NONE)
    assertThat(controller.imageEditorState.isZoomed).isFalse()
  }

  @Test
  fun `Given a zoomed canvas, when zoom mode is exited, then the zoom is dropped`() = runZoomTest {
    controller.pinch(scaleFactor = 2f)

    controller.exitZoomMode()

    assertThat(controller.mode).isEqualTo(ImageController.Mode.NONE)
    assertThat(controller.imageEditorState.isZoomed).isFalse()
  }

  @Test
  fun `Given a zoomed canvas, when zoom mode is exited, then the canvas eases back rather than snapping`() = runZoomTest {
    controller.pinch(scaleFactor = 4f)

    val settle = launch { controller.exitZoomMode() }
    delay(50)

    assertThat(controller.isSettlingZoom).isTrue()
    assertThat(controller.imageEditorState.isZoomed).isTrue()
    assertThat(controller.mode).isEqualTo(ImageController.Mode.ZOOM)

    settle.join()

    assertThat(controller.isSettlingZoom).isFalse()
    assertThat(controller.mode).isEqualTo(ImageController.Mode.NONE)
    assertThat(controller.imageEditorState.isZoomed).isFalse()
  }

  @Test
  fun `Given an easing canvas, when it is panned, then the settle is left alone`() = runZoomTest {
    controller.pinch(scaleFactor = 4f)

    val settle = launch { controller.exitZoomMode() }
    delay(50)
    controller.panBy(100f, 100f)
    settle.join()

    assertThat(controller.mode).isEqualTo(ImageController.Mode.NONE)
    assertThat(controller.imageEditorState.isZoomed).isFalse()
  }

  @Test
  fun `Given a zoomed canvas, when an edit mode is entered, then the zoom does not survive into it`() {
    controller.pinch(scaleFactor = 2f)

    controller.beginDrawEdit()

    assertThat(controller.mode).isEqualTo(ImageController.Mode.DRAW)
    assertThat(controller.imageEditorState.isZoomed).isFalse()
  }

  @Test
  fun `Given an edit mode, when the canvas is pinched, then nothing zooms`() {
    controller.beginCropAndRotateEdit()

    controller.pinch(scaleFactor = 2f)

    assertThat(controller.mode).isEqualTo(ImageController.Mode.CROP)
    assertThat(controller.imageEditorState.isZoomed).isFalse()
  }

  @Test
  fun `Given a selected element, when the canvas is pinched, then the selection is given up`() {
    val element = controller.selectNewTextElement()
    assertThat(controller.selectedElement).isNotNull()

    controller.pinch(scaleFactor = 2f)

    assertThat(controller.selectedElement).isNull()
    assertThat(controller.mode).isEqualTo(ImageController.Mode.ZOOM)

    controller.onEntityDown(element)

    assertThat(controller.selectedElement).isNull()
  }

  @Test
  fun `Given a zoomed canvas, when it is tapped, then the chrome comes back until it is tapped again`() {
    controller.pinch(scaleFactor = 2f)
    assertThat(controller.isChromeFadedForZoom).isTrue()

    controller.toggleChromeRevealed()
    assertThat(controller.isChromeFadedForZoom).isFalse()

    controller.toggleChromeRevealed()
    assertThat(controller.isChromeFadedForZoom).isTrue()
  }

  @Test
  fun `Given revealed chrome, when zoom mode is left, then the chrome goes back to following the mode`() = runZoomTest {
    controller.pinch(scaleFactor = 2f)
    controller.toggleChromeRevealed()

    controller.exitZoomMode()
    controller.pinch(scaleFactor = 2f)

    assertThat(controller.isChromeFadedForZoom).isTrue()
  }

  /** The zoom settle animation needs a frame clock, which the test scheduler drives through virtual time. */
  @OptIn(ExperimentalTestApi::class)
  private fun runZoomTest(testBody: suspend CoroutineScope.() -> Unit) = runTest {
    withContext(TestMonotonicFrameClock(this)) {
      testBody()
    }
  }

  private fun ImageController.pinch(scaleFactor: Float) {
    zoomBy(focusX = 0f, focusY = 0f, scaleFactor = scaleFactor, panX = 0f, panY = 0f)
  }

  /** Leaves a real, model-backed element selected the way a tap on it at rest would. */
  private fun ImageController.selectNewTextElement(): EditorElement {
    enterTextMode()
    onTextChanged("hello")
    val element = textEditingElement!!
    finishTextEditing()
    onEntityDown(element)

    return element
  }
}
