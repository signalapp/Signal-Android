/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit

import android.app.Application
import androidx.compose.runtime.snapshots.Snapshot
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.signal.imageeditor.core.model.EditorElement
import org.signal.imageeditor.core.model.EditorModel
import org.signal.imageeditor.core.stickers.AnalogClockStickerRenderer
import kotlin.time.Duration.Companion.seconds

private const val THUMBNAIL_SIZE_PX = 48

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
// The editor hierarchy builds an inverse-fill Path for the crop mask, which the legacy graphics shadows cannot do.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageControllerThumbnailTest {

  private val model = EditorModel.create(0).apply { addElement(EditorElement(AnalogClockStickerRenderer(0L))) }
  private val controller = ImageController(model)

  @Test
  fun `Given an unedited image, when a session is committed with nothing drawn, then there is no override`() = runTest {
    keepThumbnailCurrent()

    controller.beginDrawEdit()
    controller.commitEdit()
    settle()

    assertThat(controller.thumbnailOverride).isNull()
  }

  @Test
  fun `Given a sticker placed mid-session, when the session is still open, then there is no override`() = runTest {
    keepThumbnailCurrent()

    controller.beginDrawEdit()
    insertSticker()
    settle()

    assertThat(controller.thumbnailOverride).isNull()
  }

  @Test
  fun `Given a sticker placed mid-session, when the session is committed, then the override fills the rail's box`() = runTest {
    keepThumbnailCurrent()

    controller.beginDrawEdit()
    insertSticker()
    controller.commitEdit()
    settle()

    assertThat(controller.thumbnailOverride)
      .isNotNull()
      .transform { minOf(it.width, it.height) }
      .isEqualTo(THUMBNAIL_SIZE_PX)
  }

  @Test
  fun `Given an override, when another edit is committed, then it is replaced`() = runTest {
    keepThumbnailCurrent()
    insertSticker()
    settle()
    val first = controller.thumbnailOverride

    insertSticker()
    settle()

    assertThat(first).isNotNull()
    assertThat(controller.thumbnailOverride).isNotNull().isNotSameInstanceAs(first)
  }

  @Test
  fun `Given a model restored with edits, when the thumbnail is first kept current, then it gets an override`() = runTest {
    model.addElement(EditorElement(AnalogClockStickerRenderer(0L)))
    val restored = ImageController(model)

    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      restored.keepThumbnailCurrent(ApplicationProvider.getApplicationContext(), THUMBNAIL_SIZE_PX)
    }
    settle()

    assertThat(restored.thumbnailOverride).isNotNull()
  }

  private fun insertSticker() {
    controller.enterStickerMode()
    controller.insertSticker(AnalogClockStickerRenderer(0L))
  }

  private fun TestScope.keepThumbnailCurrent() {
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      controller.keepThumbnailCurrent(ApplicationProvider.getApplicationContext(), THUMBNAIL_SIZE_PX)
    }
  }

  private fun TestScope.settle() {
    Snapshot.sendApplyNotifications()
    advanceTimeBy(1.seconds)
    runCurrent()
  }
}
