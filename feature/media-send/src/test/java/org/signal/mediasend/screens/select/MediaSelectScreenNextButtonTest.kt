/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.select

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.contains
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
 * Covers the picker's use of the button it shares with the capture screen. The button itself is covered where it lives;
 * what matters here is that this screen puts it up, that it reads this screen's selection, and that it raises what this
 * screen expects rather than what the capture screen does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w400dp-h800dp")
class MediaSelectScreenNextButtonTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  @get:Rule
  val mediaSendDependenciesRule = MediaSendDependenciesRule(ApplicationProvider.getApplicationContext())

  private val events = mutableListOf<MediaSelectScreenEvents>()

  @Test
  fun `Given a selection, when displayed, then the button says how much is in it`() {
    setContent(selectedMedia = MEDIA.take(3))

    composeTestRule.onNodeWithTag(TestTags.MEDIA_SEND_NEXT_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.MEDIA_SEND_MEDIA_COUNT).assertTextEquals("3")
  }

  /** The picker moves on to the editor, which is a different event from the capture screen's. */
  @Test
  fun `Given a selection, when the button is clicked, then the editor is asked for`() {
    setContent(selectedMedia = MEDIA.take(1))

    composeTestRule.onNodeWithTag(TestTags.MEDIA_SEND_NEXT_BUTTON).performClick()

    assertThat(events).contains(MediaSelectScreenEvents.NavigateToEdit)
  }

  @Test
  fun `Given nothing is selected, when displayed, then there is no button to move on with`() {
    setContent(selectedMedia = emptyList())

    composeTestRule.onNodeWithTag(TestTags.MEDIA_SEND_NEXT_BUTTON).assertDoesNotExist()
  }

  private fun setContent(selectedMedia: List<Media>) {
    composeTestRule.setContent {
      SignalTheme {
        Box(modifier = Modifier.size(SCREEN_WIDTH.dp, SCREEN_HEIGHT.dp)) {
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

    composeTestRule.waitForIdle()
  }

  private companion object {
    private const val SCREEN_WIDTH = 400f
    private const val SCREEN_HEIGHT = 800f

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
        fileName = null
      )
    }
  }
}
