/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.select

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
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
 * Covers the selected media rail following the newest selection. The rail is narrower than the selection it holds well
 * before the selection is large, so whether the newest thumbnail is on screen is not something the state can be asked.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w400dp-h800dp")
class MediaSelectScreenRailTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  @get:Rule
  val mediaSendDependenciesRule = MediaSendDependenciesRule(ApplicationProvider.getApplicationContext())

  private val selectionAdditions = Channel<Media>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val selectedMedia = MEDIA.take(OVERFLOWING_SELECTION).toMutableStateList()

  @Test
  fun `Given a rail that overflows, when an addition is announced, then the newest thumbnail is on screen`() {
    setContent()
    assertNotShown(MEDIA.last())

    select(MEDIA.last())

    composeTestRule.onNodeWithTag(tagFor(MEDIA.last())).assertIsDisplayed()
  }

  @Test
  fun `Given a rail that overflows, when the selection grows unannounced, then the rail stays where it was`() {
    setContent()

    selectedMedia += MEDIA.last()
    composeTestRule.waitForIdle()

    assertNotShown(MEDIA.last())
    composeTestRule.onNodeWithTag(tagFor(MEDIA.first())).assertIsDisplayed()
  }

  @Test
  fun `Given a rail scrolled to the newest, when another addition is announced, then it follows on`() {
    setContent()
    select(MEDIA[OVERFLOWING_SELECTION])
    composeTestRule.onNodeWithTag(tagFor(MEDIA[OVERFLOWING_SELECTION])).assertIsDisplayed()

    select(MEDIA.last())

    composeTestRule.onNodeWithTag(tagFor(MEDIA.last())).assertIsDisplayed()
  }

  /** Adds [media] to the selection the way the flow does: the state grows, and the addition is announced. */
  private fun select(media: Media) {
    selectedMedia += media
    selectionAdditions.trySend(media)
    composeTestRule.waitForIdle()
  }

  /** Off-viewport rail items are never composed, so being absent and being off screen both count as not shown. */
  private fun assertNotShown(media: Media) {
    val tag = tagFor(media)
    if (composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()) {
      composeTestRule.onNodeWithTag(tag).assertIsNotDisplayed()
    }
  }

  private fun tagFor(media: Media): String = TestTags.selectedMediaThumbnail(media.uri.toString())

  private fun setContent() {
    composeTestRule.setContent {
      SignalTheme {
        Box(modifier = Modifier.size(RAIL_WIDTH.dp, RAIL_HEIGHT.dp)) {
          MediaSelectScreen(
            state = MediaSelectState.Files(
              selectedMediaFolder = FOLDER,
              selectedMediaFolderItems = MEDIA,
              selectedMedia = selectedMedia
            ),
            onEvent = {},
            selectionAdditions = selectionAdditions.receiveAsFlow()
          )
        }
      }
    }

    composeTestRule.waitForIdle()
  }

  private companion object {
    private const val RAIL_WIDTH = 400f
    private const val RAIL_HEIGHT = 800f

    /** Enough thumbnails at 44dp plus 12dp of spacing to run past the end of a rail this wide several times over. */
    private const val OVERFLOWING_SELECTION = 12

    private val FOLDER = MediaFolder(
      thumbnailUri = "content://folder".toUri(),
      title = "Camera",
      itemCount = 16,
      bucketId = "bucket",
      folderType = MediaFolder.FolderType.CAMERA
    )

    private val MEDIA: List<Media> = (0 until 16).map { index ->
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
