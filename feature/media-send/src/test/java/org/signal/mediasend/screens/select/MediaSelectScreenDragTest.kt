/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.select

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import org.signal.core.models.media.MediaFolder
import org.signal.core.ui.CoreUiDependenciesRule
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.mediasend.MediaSendDependenciesRule
import org.signal.mediasend.test.TestTags

/**
 * Covers the gallery grid's press-and-drag range selection: the gesture has to survive the tiles' own click handling and
 * take the drag away from the grid's scrolling, neither of which is visible from the state alone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MediaSelectScreenDragTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  @get:Rule
  val mediaSendDependenciesRule = MediaSendDependenciesRule(ApplicationProvider.getApplicationContext())

  private val events = mutableListOf<MediaSelectScreenEvents>()

  private val selected: Set<Media>
    get() = events.filterIsInstance<MediaSelectScreenEvents.MediaSelected>().flatMap { it.media }.toSet()

  private val unselected: Set<Media>
    get() = events.filterIsInstance<MediaSelectScreenEvents.MediaUnselected>().flatMap { it.media }.toSet()

  @Test
  fun `Given an unselected tile, when it is long pressed, then it alone is selected`() {
    setContent()

    composeTestRule.onNodeWithTag(TestTags.MEDIA_SELECT_GRID).performTouchInput {
      down(tileCenter(0))
      advanceEventTime(LONG_PRESS_MS)
      up()
    }
    composeTestRule.waitForIdle()

    assertEquals(setOf(media(0)), selected)
    assertEquals(emptySet<Media>(), unselected)
  }

  @Test
  fun `Given a long press, when dragging across tiles, then the range is selected`() {
    setContent()

    composeTestRule.onNodeWithTag(TestTags.MEDIA_SELECT_GRID).performTouchInput {
      down(tileCenter(0))
      advanceEventTime(LONG_PRESS_MS)
      moveTo(tileCenter(1))
      moveTo(tileCenter(2))
      up()
    }
    composeTestRule.waitForIdle()

    assertEquals(setOf(media(0), media(1), media(2)), selected)
    assertEquals(emptySet<Media>(), unselected)
  }

  @Test
  fun `Given a range has been dragged out, when retracting back over it, then those tiles are unselected`() {
    setContent()

    composeTestRule.onNodeWithTag(TestTags.MEDIA_SELECT_GRID).performTouchInput {
      down(tileCenter(0))
      advanceEventTime(LONG_PRESS_MS)
      moveTo(tileCenter(2))
      moveTo(tileCenter(1))
      up()
    }
    composeTestRule.waitForIdle()

    assertEquals(setOf(media(0), media(1), media(2)), selected)
    assertEquals(setOf(media(2)), unselected)
  }

  @Test
  fun `Given a selected tile, when it is long pressed, then it is unselected and no range is dragged out`() {
    setContent(selectedMedia = listOf(media(0)))

    composeTestRule.onNodeWithTag(TestTags.MEDIA_SELECT_GRID).performTouchInput {
      down(tileCenter(0))
      advanceEventTime(LONG_PRESS_MS)
      moveTo(tileCenter(1))
      up()
    }
    composeTestRule.waitForIdle()

    assertEquals(setOf(media(0)), unselected)
    assertEquals(emptySet<Media>(), selected)
  }

  /**
   * Read off the laid-out tiles rather than derived from the column count, so the gesture lands where the tiles actually
   * are whatever width the grid ended up.
   */
  private fun tileCenter(index: Int): Offset {
    val grid = composeTestRule.onNodeWithTag(TestTags.MEDIA_SELECT_GRID).fetchSemanticsNode()
    val tile = grid.children[index]
    val offset = tile.positionInRoot - grid.positionInRoot

    return Offset(offset.x + tile.size.width / 2f, offset.y + tile.size.height / 2f)
  }

  private fun media(index: Int): Media = MEDIA[index]

  private fun setContent(selectedMedia: List<Media> = emptyList()) {
    composeTestRule.setContent {
      SignalTheme {
        Box(modifier = Modifier.size(GRID_WIDTH.dp, GRID_HEIGHT.dp)) {
          MediaSelectScreen(
            state = MediaSelectState.Files(
              selectedMediaFolder = FOLDER,
              selectedMediaFolderItems = MEDIA,
              selectedMedia = selectedMedia
            ),
            onEvent = { events += it }
          )
        }
      }
    }
  }

  private companion object {
    private const val LONG_PRESS_MS = 600L
    private const val GRID_WIDTH = 400f
    private const val GRID_HEIGHT = 800f

    private val FOLDER = MediaFolder(
      thumbnailUri = "content://folder".toUri(),
      title = "Camera",
      itemCount = 8,
      bucketId = "bucket",
      folderType = MediaFolder.FolderType.CAMERA
    )

    private val MEDIA: List<Media> = (0 until 8).map { index ->
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
