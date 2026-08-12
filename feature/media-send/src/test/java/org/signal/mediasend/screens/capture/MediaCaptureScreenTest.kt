/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.capture

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.media.Media
import org.signal.core.ui.CoreUiDependenciesRule
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.mediasend.MediaSendDependenciesRule
import org.signal.mediasend.MediaSendFlowActivityContract
import org.signal.mediasend.MediaSendRoute
import org.signal.mediasend.test.TestTags

/**
 * Covers the chrome the flow adds over a capture screen: which bar is offered, to which flows, and what it raises.
 *
 * The bars are rendered on the text story route, so that they are what is under test rather than the camera behind
 * them. Which of the two the route actually puts up is covered on its own.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w400dp-h800dp")
class MediaCaptureScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  @get:Rule
  val mediaSendDependenciesRule = MediaSendDependenciesRule(ApplicationProvider.getApplicationContext())

  private val events = mutableListOf<MediaCaptureScreenEvents>()

  @Test
  fun `Given a camera-first flow with nothing captured, when displayed, then the toggle is offered`() {
    setContent(cameraFirstState())

    composeTestRule.onNodeWithTag(TestTags.MEDIA_CAPTURE_CAMERA_TOGGLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.MEDIA_CAPTURE_TEXT_STORY_TOGGLE).assertIsDisplayed()
  }

  @Test
  fun `Given a flow headed straight to a chat, when displayed, then no bar is offered`() {
    setContent(cameraFirstState().copy(mode = MediaSendFlowActivityContract.Mode.SingleRecipient, isStory = false))

    composeTestRule.onNodeWithTag(TestTags.MEDIA_CAPTURE_SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.MEDIA_CAPTURE_CAMERA_TOGGLE).assertDoesNotExist()
  }

  @Test
  fun `Given a flow that is not camera-first, when displayed, then no bar is offered`() {
    setContent(cameraFirstState().copy(isCameraFirst = false))

    composeTestRule.onNodeWithTag(TestTags.MEDIA_CAPTURE_CAMERA_TOGGLE).assertDoesNotExist()
  }

  @Test
  fun `when the camera is picked from the toggle, then it is asked for`() {
    setContent(cameraFirstState())

    composeTestRule.onNodeWithTag(TestTags.MEDIA_CAPTURE_CAMERA_TOGGLE).performClick()

    assertThat(events).containsExactly(MediaCaptureScreenEvents.ShowCamera)
  }

  @Test
  fun `when the text story is picked from the toggle, then it is asked for`() {
    setContent(cameraFirstState())

    composeTestRule.onNodeWithTag(TestTags.MEDIA_CAPTURE_TEXT_STORY_TOGGLE).performClick()

    assertThat(events).containsExactly(MediaCaptureScreenEvents.ShowTextStory)
  }

  @Test
  fun `Given something has been captured, when displayed, then the media bar replaces the toggle`() {
    setContent(cameraFirstState().copy(selectedMedia = listOf(MEDIA)))

    composeTestRule.onNodeWithTag(TestTags.MEDIA_CAPTURE_MEDIA_COUNT).assertTextEquals("1 item")
    composeTestRule.onNodeWithTag(TestTags.MEDIA_CAPTURE_CAMERA_TOGGLE).assertDoesNotExist()
  }

  @Test
  fun `Given something has been captured, when next is clicked, then the flow is asked to move on`() {
    setContent(cameraFirstState().copy(selectedMedia = listOf(MEDIA)))

    composeTestRule.onNodeWithTag(TestTags.MEDIA_CAPTURE_NEXT_BUTTON).performClick()

    assertThat(events).containsExactly(MediaCaptureScreenEvents.NextClicked)
  }

  @Test
  fun `Given the text story route, when displayed, then the editor is what fills the screen`() {
    setContent(cameraFirstState())

    composeTestRule.onNodeWithTag(TEXT_STORY_SLOT).assertIsDisplayed()
    assertThat(events).isEmpty()
  }

  /** The camera is the fallback for every capture route that is not the text story, including the flow's chrome key. */
  @Test
  fun `Given the camera route, when displayed, then the text story editor is not what fills the screen`() {
    setContent(cameraFirstState().copy(selectedCaptureScreen = MediaSendRoute.Capture.Camera))

    composeTestRule.onNodeWithTag(TEXT_STORY_SLOT).assertDoesNotExist()
    composeTestRule.onNodeWithTag(TestTags.MEDIA_CAPTURE_SCREEN).assertIsDisplayed()
  }

  @Test
  fun `Given the camera route, when displayed, then the flow's chrome sits over it`() {
    setContent(cameraFirstState().copy(selectedCaptureScreen = MediaSendRoute.Capture.Camera))

    composeTestRule.onNodeWithTag(TestTags.MEDIA_CAPTURE_CAMERA_TOGGLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.MEDIA_CAPTURE_TEXT_STORY_TOGGLE).assertIsDisplayed()
  }

  private fun cameraFirstState() = MediaCaptureState(
    selectedCaptureScreen = MediaSendRoute.Capture.TextStory,
    isCameraFirst = true,
    storiesEnabled = true,
    mode = MediaSendFlowActivityContract.Mode.ChooseAfterMediaSelection
  )

  private fun setContent(state: MediaCaptureState) {
    composeTestRule.setContent {
      SignalTheme {
        MediaCaptureScreen(
          state = state,
          onEvent = { events += it },
          textStoryEditorSlot = {
            Box(
              modifier = Modifier
                .fillMaxSize()
                .testTag(TEXT_STORY_SLOT)
            )
          }
        )
      }
    }

    composeTestRule.waitForIdle()
  }

  private companion object {
    private const val TEXT_STORY_SLOT = "text_story_slot"

    private val MEDIA = Media(
      uri = "content://capture".toUri(),
      contentType = "image/jpeg",
      date = 0,
      width = 100,
      height = 200,
      size = 1024,
      duration = 0,
      isBorderless = false,
      isVideoGif = false,
      bucketId = Media.ALL_MEDIA_BUCKET_ID,
      caption = null,
      transformProperties = null,
      fileName = null
    )
  }
}
