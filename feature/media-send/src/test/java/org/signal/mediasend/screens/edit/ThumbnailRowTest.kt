/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.media.Media
import org.signal.core.ui.CoreUiDependenciesRule
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.mediasend.test.TestTags

/**
 * Covers the delete affordance staying on the media the pager is showing for the length of a reorder. The row renders
 * the order the drag has built up while the pager stays on the pre-drag one until the drop lands in state, so whether
 * the two still agree about which thumbnail is the focused one is not something either of them can be asked alone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w400dp-h800dp")
class ThumbnailRowTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  private val reorders = mutableListOf<Pair<Int, Int>>()

  @Test
  fun `Given the focused thumbnail is dragged past its neighbour, when it has not been dropped, then the delete affordance is still on it`() {
    setContent()

    composeTestRule.onNodeWithTag(HOST).performTouchInput {
      down(thumbnailCenter(0))
      advanceEventTime(LONG_PRESS_MS)
      moveTo(thumbnailCenter(1) + Offset(overshootPx, 0f))
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(deleteIconTagFor(0)).assertExists()
    composeTestRule.onNodeWithTag(deleteIconTagFor(1)).assertDoesNotExist()
  }

  /** Guards the test above from passing on a drag that never swapped anything. */
  @Test
  fun `Given the focused thumbnail is dragged past its neighbour, when it is dropped, then the two have traded places`() {
    setContent()

    composeTestRule.onNodeWithTag(HOST).performTouchInput {
      down(thumbnailCenter(0))
      advanceEventTime(LONG_PRESS_MS)
      moveTo(thumbnailCenter(1) + Offset(overshootPx, 0f))
      up()
    }
    composeTestRule.waitForIdle()

    assertEquals(listOf(0 to 1), reorders)
  }

  /**
   * Read off the laid-out thumbnails rather than derived from their size, so the gesture lands where they actually are
   * once the row's fish-eye padding has had its say.
   */
  private fun thumbnailCenter(index: Int): Offset {
    val thumbnail = composeTestRule.onAllNodes(hasClickAction())[index].fetchSemanticsNode()

    return Offset(
      thumbnail.positionInRoot.x + thumbnail.size.width / 2f,
      thumbnail.positionInRoot.y + thumbnail.size.height / 2f
    )
  }

  /** Enough past the neighbour's center to be clear of the midpoint a swap is decided on. */
  private val overshootPx: Float
    get() = with(composeTestRule.density) { OVERSHOOT.toPx() }

  private fun deleteIconTagFor(index: Int): String = TestTags.thumbnailRowDeleteIcon(MEDIA[index].uri.toString())

  /**
   * Inspected rather than loaded: a thumbnail emits nothing at all until Glide has something to put in it, and there is
   * nothing to load here, so the row would otherwise lay out as a strip of empty slots with no geometry for a drag to
   * work against.
   */
  private fun setContent() {
    composeTestRule.setContent {
      SignalTheme {
        CompositionLocalProvider(LocalInspectionMode provides true) {
          Box(modifier = Modifier.size(ROW_WIDTH.dp, ROW_HEIGHT.dp).testTag(HOST)) {
            ThumbnailRow(
              selectedMedia = MEDIA,
              pagerState = rememberPagerState(initialPage = 0, pageCount = { MEDIA.size }),
              onReorder = { fromIndex, toIndex -> reorders += fromIndex to toIndex }
            )
          }
        }
      }
    }

    composeTestRule.waitForIdle()
  }

  private companion object {
    private const val HOST = "thumbnail_row_host"
    private const val LONG_PRESS_MS = 600L
    private const val ROW_WIDTH = 400f
    private const val ROW_HEIGHT = 100f

    private val OVERSHOOT = 10.dp

    private val MEDIA: List<Media> = (0 until 3).map { index ->
      Media(
        uri = "content://media/$index".toUri(),
        contentType = "image/jpeg",
        date = index.toLong(),
        width = 100,
        height = 100,
        size = 1024,
        duration = 0,
        isBorderless = false,
        isVideoGif = false,
        bucketId = "bucket",
        caption = null,
        transformProperties = null,
        fileName = "media_$index.jpg"
      )
    }
  }
}
