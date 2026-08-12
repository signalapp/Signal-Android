/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.capture

import android.app.Application
import androidx.annotation.StringRes
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.media.Media
import org.signal.core.util.SeekableFileDescriptor
import org.signal.mediasend.MediaSendDependenciesRule
import org.signal.mediasend.MediaSendFlowActivityContract
import org.signal.mediasend.MediaSendFlowEvent
import org.signal.mediasend.MediaSendFlowState
import org.signal.mediasend.MediaSendRoute
import org.signal.mediasend.R
import org.signal.mediasend.SnackbarEvent
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Covers the two halves of the capture screen's wiring: the parts of the flow's state it mirrors, and what it asks the
 * flow to do with the media it captures.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class MediaCaptureViewModelTest {

  @get:Rule
  val mediaSendDependenciesRule = MediaSendDependenciesRule(ApplicationProvider.getApplicationContext())

  private val testDispatcher = StandardTestDispatcher()
  private val repository: MediaCaptureRepository = mockk(relaxed = true)
  private val parentEvents = mutableListOf<MediaSendFlowEvent>()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `Given a camera-first flow that has yet to pick a destination, when created, then the bottom bar can display`() = runTest {
    val viewModel = createViewModel(cameraFirstStoryCapableState())

    assertThat(viewModel.state.value.canDisplayBottomBar).isTrue()
  }

  @Test
  fun `Given a camera-first flow headed straight to a chat, when created, then the bottom bar stays hidden`() = runTest {
    val viewModel = createViewModel(cameraFirstStoryCapableState().copy(mode = MediaSendFlowActivityContract.Mode.SingleRecipient, isStory = false))

    assertThat(viewModel.state.value.canDisplayBottomBar).isFalse()
  }

  @Test
  fun `Given a story flow, when created, then recording is capped at the story limit`() = runTest {
    val viewModel = createViewModel(MediaSendFlowState(isStory = true, storyMaxVideoDuration = 30.seconds))

    assertThat(viewModel.state.value.maxVideoDurationSecondsOverride).isEqualTo(30)
  }

  @Test
  fun `Given a chat flow, when created, then recording keeps the device's own cap`() = runTest {
    val viewModel = createViewModel(MediaSendFlowState(isStory = false, storyMaxVideoDuration = 30.seconds))

    assertThat(viewModel.state.value.maxVideoDurationSecondsOverride).isEqualTo(0)
  }

  @Test
  fun `when the flow's selection changes, then the screen's copy of it follows`() = runTest {
    val parentState = MutableStateFlow(MediaSendFlowState())
    val viewModel = createViewModel(parentState)

    parentState.value = MediaSendFlowState(selectedMedia = listOf(MEDIA))
    advanceUntilIdle()

    assertThat(viewModel.state.value.selectedMedia).containsExactly(MEDIA)
  }

  @Test
  fun `when navigation moves to the text story editor, then the screen follows`() = runTest {
    val viewModel = createViewModel()

    viewModel.onEvent(MediaCaptureScreenEvents.SelectedCaptureScreenChanged(MediaSendRoute.Capture.TextStory))
    advanceUntilIdle()

    assertThat(viewModel.state.value.selectedCaptureScreen).isEqualTo(MediaSendRoute.Capture.TextStory)
  }

  @Test
  fun `when the camera is asked for, then the flow is sent to it`() = runTest {
    onEvent(MediaCaptureScreenEvents.ShowCamera)

    assertThat(parentEvents).containsExactly(MediaSendFlowEvent.NavigateToCamera)
  }

  @Test
  fun `when the text story editor is asked for, then the flow is sent to it`() = runTest {
    onEvent(MediaCaptureScreenEvents.ShowTextStory)

    assertThat(parentEvents).containsExactly(MediaSendFlowEvent.NavigateToTextStory)
  }

  @Test
  fun `when next is clicked, then the flow moves on to the editor`() = runTest {
    onEvent(MediaCaptureScreenEvents.NextClicked)

    assertThat(parentEvents).containsExactly(MediaSendFlowEvent.NavigateToEdit)
  }

  @Test
  fun `when the gallery is opened from the camera, then the flow is sent to it`() = runTest {
    onCameraEvent(CameraXScreenEvents.GalleryClicked)

    assertThat(parentEvents).containsExactly(MediaSendFlowEvent.NavigateToFolders)
  }

  @Test
  fun `when the camera is closed, then the flow is asked to close`() = runTest {
    onCameraEvent(CameraXScreenEvents.CameraCloseClicked)

    assertThat(parentEvents).containsExactly(MediaSendFlowEvent.CloseRequested)
  }

  @Test
  fun `when a qr code is read, then it is handed to the flow`() = runTest {
    onCameraEvent(CameraXScreenEvents.QrCodeFound("sgnl://example"))

    assertThat(parentEvents).containsExactly(MediaSendFlowEvent.QrCodeScanned("sgnl://example"))
  }

  @Test
  fun `when a recording fails outright, then the failure is reported`() = runTest {
    onCameraEvent(CameraXScreenEvents.VideoCaptureError)

    assertThat(parentEvents).containsExactly(snackbar(R.string.MediaSendViewModel__error_recording_video))
  }

  @Test
  fun `when an image is captured, then the media it was written to is handed to the flow`() = runTest {
    coEvery { repository.writeCapturedImage(any(), any(), any()) } returns MEDIA

    onCameraEvent(CameraXScreenEvents.ImageCaptured(data = byteArrayOf(1, 2, 3), width = 100, height = 200))

    assertThat(parentEvents).containsExactly(MediaSendFlowEvent.MediaCaptured(MEDIA))
  }

  @Test
  fun `when an image cannot be written out, then the failure is reported and nothing is handed over`() = runTest {
    coEvery { repository.writeCapturedImage(any(), any(), any()) } returns null

    onCameraEvent(CameraXScreenEvents.ImageCaptured(data = byteArrayOf(1, 2, 3), width = 100, height = 200))

    assertThat(parentEvents).containsExactly(snackbar(R.string.MediaSendViewModel__error_taking_photo))
  }

  @Test
  fun `when a recording is captured, then it is handed over with how long it ran`() = runTest {
    coEvery { repository.writeCapturedVideo(any()) } returns MEDIA

    onCameraEvent(CameraXScreenEvents.VideoCaptured(fd = mockk(relaxed = true), durationMs = 4_000))

    assertThat(parentEvents).containsExactly(MediaSendFlowEvent.MediaCaptured(MEDIA, 4_000.milliseconds))
  }

  @Test
  fun `when a recording cannot be written out, then the failure is reported and nothing is handed over`() = runTest {
    coEvery { repository.writeCapturedVideo(any<SeekableFileDescriptor>()) } returns null

    onCameraEvent(CameraXScreenEvents.VideoCaptured(fd = mockk(relaxed = true), durationMs = 4_000))

    assertThat(parentEvents).containsExactly(snackbar(R.string.MediaSendViewModel__error_recording_video))
  }

  @Test
  fun `Given nothing has happened, when created, then the flow is left alone`() = runTest {
    createViewModel()
    advanceUntilIdle()

    assertThat(parentEvents).isEmpty()
  }

  /** Raises [event] on a freshly created screen and lets it settle. */
  private fun TestScope.onEvent(event: MediaCaptureScreenEvents) {
    createViewModel().onEvent(event)
    advanceUntilIdle()
  }

  private fun TestScope.onCameraEvent(event: CameraXScreenEvents) {
    onEvent(MediaCaptureScreenEvents.Camera(event))
  }

  private fun snackbar(@StringRes message: Int) = MediaSendFlowEvent.ShowSnackbar(SnackbarEvent(message = message))

  private fun cameraFirstStoryCapableState() = MediaSendFlowState(
    isCameraFirst = true,
    storiesEnabled = true,
    mode = MediaSendFlowActivityContract.Mode.ChooseAfterMediaSelection
  )

  private fun createViewModel(parentState: MediaSendFlowState = MediaSendFlowState()) = createViewModel(MutableStateFlow(parentState))

  private fun createViewModel(parentState: MutableStateFlow<MediaSendFlowState>): MediaCaptureViewModel {
    return MediaCaptureViewModel(
      parentState = parentState,
      parentEventEmitter = { parentEvents += it },
      selectedCaptureScreen = MediaSendRoute.Capture.Camera,
      repository = repository
    )
  }

  private companion object {
    private val MEDIA = Media(
      uri = "content://capture".toUri(),
      contentType = "image/jpeg",
      date = 0,
      width = 100,
      height = 200,
      size = 3,
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
