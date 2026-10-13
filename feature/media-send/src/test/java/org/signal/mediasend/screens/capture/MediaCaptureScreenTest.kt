/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.capture

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isCloseTo
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
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
 * The bars are rendered on the text story route so they are what is under test rather than the camera behind them.
 * Which of the two the route puts up is covered separately.
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
  fun `Given a flow that offers every mode, when displayed, then each one is on the bar`() {
    setContent(cameraFirstState())

    composeTestRule.onNodeWithTag(TestTags.MEDIA_CAPTURE_PHOTO_TOGGLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.MEDIA_CAPTURE_VIDEO_TOGGLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.MEDIA_CAPTURE_TEXT_STORY_TOGGLE).assertIsDisplayed()
  }

  @Test
  fun `Given a flow that offers every mode, when displayed, then the selected one is centered under the highlight`() {
    setContent(cameraFirstState())

    val screen = composeTestRule.onNodeWithTag(TestTags.MEDIA_CAPTURE_SCREEN).getUnclippedBoundsInRoot()
    val selected = composeTestRule.onNodeWithTag(TestTags.MEDIA_CAPTURE_TEXT_STORY_TOGGLE).getUnclippedBoundsInRoot()

    assertThat(selected.centerX.value).isCloseTo(screen.centerX.value, 1f)
  }

  @Test
  fun `when the bar is swiped, then nothing is asked for until the swipe is released`() {
    setContent(cameraFirstState())

    composeTestRule.onNodeWithTag(TestTags.MEDIA_CAPTURE_MODE_BAR).performTouchInput {
      down(centerLeft)
      moveBy(Offset(x = width.toFloat(), y = 0f))
    }
    composeTestRule.waitForIdle()

    assertThat(events).isEmpty()

    composeTestRule.onNodeWithTag(TestTags.MEDIA_CAPTURE_MODE_BAR).performTouchInput { up() }
    composeTestRule.waitForIdle()

    assertThat(events).containsExactly(MediaCaptureScreenEvents.CaptureModeSelected(MediaCaptureMode.VIDEO))
  }

  @Test
  fun `when the bar is swiped and released, then the mode under the center is what is asked for`() {
    setContent(cameraFirstState().copy(selectedCaptureScreen = MediaSendRoute.Capture.Camera, selectedCameraMode = MediaCaptureMode.PHOTO))

    composeTestRule.onNodeWithTag(TestTags.MEDIA_CAPTURE_MODE_BAR).performTouchInput {
      swipeLeft(startX = centerRight.x, endX = centerLeft.x)
    }
    composeTestRule.waitForIdle()

    assertThat(captureModeSelections).containsExactly(MediaCaptureMode.TEXT_STORY)
  }

  @Test
  fun `Given a flow headed straight to a chat, when displayed, then the text story is not on the bar`() {
    setContent(cameraFirstState().copy(mode = MediaSendFlowActivityContract.Mode.SingleRecipient, isStory = false))

    composeTestRule.onNodeWithTag(TestTags.MEDIA_CAPTURE_SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.MEDIA_CAPTURE_TEXT_STORY_TOGGLE).assertDoesNotExist()
  }

  /** A single mode has nothing to switch between, so no bar is put up. */
  @Test
  fun `Given a flow with only one mode, when displayed, then no bar is offered`() {
    setContent(cameraFirstState().copy(isCameraFirst = false, isVideoCaptureSupported = false))

    composeTestRule.onNodeWithTag(TestTags.MEDIA_CAPTURE_PHOTO_TOGGLE).assertDoesNotExist()
    composeTestRule.onNodeWithTag(TestTags.MEDIA_CAPTURE_TEXT_STORY_TOGGLE).assertDoesNotExist()
  }

  @Test
  fun `Given a device that cannot record, when displayed, then video is not on the bar`() {
    setContent(cameraFirstState().copy(isVideoCaptureSupported = false))

    composeTestRule.onNodeWithTag(TestTags.MEDIA_CAPTURE_PHOTO_TOGGLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.MEDIA_CAPTURE_VIDEO_TOGGLE).assertDoesNotExist()
  }

  @Test
  fun `when a mode is picked from the bar, then it is asked for`() {
    setContent(cameraFirstState())

    composeTestRule.onNodeWithTag(TestTags.MEDIA_CAPTURE_VIDEO_TOGGLE).performClick()

    assertThat(events).containsExactly(MediaCaptureScreenEvents.CaptureModeSelected(MediaCaptureMode.VIDEO))
  }

  @Test
  fun `Given a recording is running, when displayed, then no bar is offered`() {
    setContent(cameraFirstState().copy(selectedCaptureScreen = MediaSendRoute.Capture.Camera, isRecording = true))

    composeTestRule.onNodeWithTag(TestTags.MEDIA_CAPTURE_MODE_BAR).assertDoesNotExist()
  }

  @Test
  fun `Given a recording that has finished, when displayed, then the bar is back`() {
    setContent(cameraFirstState().copy(selectedCaptureScreen = MediaSendRoute.Capture.Camera, isRecording = false))

    composeTestRule.onNodeWithTag(TestTags.MEDIA_CAPTURE_MODE_BAR).assertIsDisplayed()
  }

  @Test
  fun `Given the text story route, when displayed, then the editor is what fills the screen`() {
    setContent(cameraFirstState())

    composeTestRule.onNodeWithTag(TEXT_STORY_SLOT).assertIsDisplayed()
    assertThat(events).isEmpty()
  }

  /** The camera is the fallback for every capture route other than the text story. */
  @Test
  fun `Given the camera route, when displayed, then the text story editor is not what fills the screen`() {
    setContent(cameraFirstState().copy(selectedCaptureScreen = MediaSendRoute.Capture.Camera))

    composeTestRule.onNodeWithTag(TEXT_STORY_SLOT).assertDoesNotExist()
    composeTestRule.onNodeWithTag(TestTags.MEDIA_CAPTURE_SCREEN).assertIsDisplayed()
  }

  @Test
  fun `Given the camera route, when displayed, then the flow's chrome sits over it`() {
    setContent(cameraFirstState().copy(selectedCaptureScreen = MediaSendRoute.Capture.Camera))

    composeTestRule.onNodeWithTag(TestTags.MEDIA_CAPTURE_PHOTO_TOGGLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.MEDIA_CAPTURE_TEXT_STORY_TOGGLE).assertIsDisplayed()
  }

  /** A text story is text alone, so a flow already carrying a capture has no way to send one. */
  @Test
  fun `Given something has been captured, when displayed, then the text story is no longer on the bar`() {
    setContent(withSelectedMedia())

    composeTestRule.onNodeWithTag(TestTags.MEDIA_CAPTURE_TEXT_STORY_TOGGLE).assertDoesNotExist()
    composeTestRule.onNodeWithTag(TestTags.MEDIA_CAPTURE_PHOTO_TOGGLE).assertIsDisplayed()
  }

  @Test
  fun `Given the text story route, when the editor's send button is tapped, then the tap reaches the editor`() {
    var sendClicks = 0

    setContent(cameraFirstState()) {
      Box(modifier = Modifier.fillMaxSize()) {
        Box(
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = TEXT_STORY_SEND_END_MARGIN, bottom = TEXT_STORY_SEND_BOTTOM_MARGIN)
            .size(TEXT_STORY_SEND_SIZE)
            .clickable { sendClicks++ }
            .testTag(TEXT_STORY_SEND)
        )
      }
    }

    composeTestRule.onNodeWithTag(TEXT_STORY_SEND).performClick()

    assertThat(sendClicks).isEqualTo(1)
  }

  //region The next button over the bar's end

  @Test
  fun `Given nothing has been captured, when displayed, then there is no next button`() {
    setContent(cameraFirstState().copy(selectedCaptureScreen = MediaSendRoute.Capture.Camera))

    composeTestRule.onNodeWithTag(TestTags.MEDIA_SEND_NEXT_BUTTON).assertDoesNotExist()
  }

  @Test
  fun `Given something has been captured, when displayed, then the next button says how much is waiting`() {
    setContent(withSelectedMedia(count = 3))

    composeTestRule.onNodeWithTag(TestTags.MEDIA_SEND_NEXT_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.MEDIA_SEND_MEDIA_COUNT).assertTextEquals("3")
  }

  @Test
  fun `Given something has been captured, when next is clicked, then the flow is asked to move on`() {
    setContent(withSelectedMedia())

    composeTestRule.onNodeWithTag(TestTags.MEDIA_SEND_NEXT_BUTTON).performClick()

    assertThat(chromeRequests).containsExactly(MediaCaptureScreenEvents.NextClicked)
  }

  @Test
  fun `Given a recording is running, when displayed, then the next button is taken away with the bar`() {
    setContent(withSelectedMedia().copy(isRecording = true))

    composeTestRule.onNodeWithTag(TestTags.MEDIA_SEND_NEXT_BUTTON).assertDoesNotExist()
  }

  //endregion

  /** The camera reports into the same stream, so what the bar asked for has to be filtered out of it. */
  private val captureModeSelections: List<MediaCaptureMode>
    get() = events.filterIsInstance<MediaCaptureScreenEvents.CaptureModeSelected>().map { it.mode }

  /** Everything the chrome asked for: the same stream with the camera's own reports removed. */
  private val chromeRequests: List<MediaCaptureScreenEvents>
    get() = events.filterNot { it is MediaCaptureScreenEvents.Camera }

  private val DpRect.centerX: Dp
    get() = (left + right) / 2

  /** A camera-first flow that has already captured something, which puts the next button up. */
  private fun withSelectedMedia(count: Int = 1) = cameraFirstState().copy(
    selectedCaptureScreen = MediaSendRoute.Capture.Camera,
    selectedMedia = List(count) { MEDIA }
  )

  private fun cameraFirstState() = MediaCaptureState(
    selectedCaptureScreen = MediaSendRoute.Capture.TextStory,
    isCameraFirst = true,
    storiesEnabled = true,
    mode = MediaSendFlowActivityContract.Mode.ChooseAfterMediaSelection
  )

  private fun setContent(
    state: MediaCaptureState,
    textStoryEditorSlot: @Composable () -> Unit = {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .testTag(TEXT_STORY_SLOT)
      )
    }
  ) {
    composeTestRule.setContent {
      SignalTheme {
        MediaCaptureScreen(
          state = state,
          onEvent = { events += it },
          textStoryEditorSlot = textStoryEditorSlot
        )
      }
    }

    composeTestRule.waitForIdle()
  }

  private companion object {
    private const val TEXT_STORY_SLOT = "text_story_slot"
    private const val TEXT_STORY_SEND = "text_story_send"

    private val TEXT_STORY_SEND_SIZE = 48.dp
    private val TEXT_STORY_SEND_END_MARGIN = 12.dp
    private val TEXT_STORY_SEND_BOTTOM_MARGIN = 16.dp

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
