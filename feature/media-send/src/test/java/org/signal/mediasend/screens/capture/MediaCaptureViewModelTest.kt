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
import io.mockk.coVerify
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

  //region Chrome the flow's configuration decides

  @Test
  fun `Given a camera-first flow that has yet to pick a destination, when created, then the bottom bar can display`() = runTest {
    val viewModel = createViewModel(cameraFirstStoryCapableState())

    assertThat(viewModel.state.value.canDisplayBottomBar).isTrue()
  }

  @Test
  fun `Given a camera-first flow aimed at one recipient's story, when created, then the bottom bar can display`() = runTest {
    val viewModel = createViewModel(
      cameraFirstStoryCapableState().copy(mode = MediaSendFlowActivityContract.Mode.SingleRecipient, isStory = true)
    )

    assertThat(viewModel.state.value.canDisplayBottomBar).isTrue()
  }

  @Test
  fun `Given a camera-first flow headed straight to a chat, when created, then the bottom bar stays hidden`() = runTest {
    val viewModel = createViewModel(
      cameraFirstStoryCapableState().copy(mode = MediaSendFlowActivityContract.Mode.SingleRecipient, isStory = false)
    )

    assertThat(viewModel.state.value.canDisplayBottomBar).isFalse()
  }

  @Test
  fun `Given stories are unavailable, when created, then the bottom bar stays hidden`() = runTest {
    val viewModel = createViewModel(cameraFirstStoryCapableState().copy(storiesEnabled = false))

    assertThat(viewModel.state.value.canDisplayBottomBar).isFalse()
  }

  @Test
  fun `Given the camera was not what opened the flow, when created, then the bottom bar stays hidden`() = runTest {
    val viewModel = createViewModel(cameraFirstStoryCapableState().copy(isCameraFirst = false))

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

  //endregion

  //region Following the flow

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

  /**
   * Which capture screen is showing is not the flow's to report, and a capture landing in the selection is exactly when
   * the flow reports something while the text story editor is open.
   */
  @Test
  fun `Given the text story editor is open, when the flow's selection changes, then it stays open`() = runTest {
    val parentState = MutableStateFlow(MediaSendFlowState())
    val viewModel = createViewModel(parentState)
    viewModel.onEvent(MediaCaptureScreenEvents.SelectedCaptureScreenChanged(MediaSendRoute.Capture.TextStory))
    advanceUntilIdle()

    parentState.value = MediaSendFlowState(selectedMedia = listOf(MEDIA))
    advanceUntilIdle()

    assertThat(viewModel.state.value.selectedCaptureScreen).isEqualTo(MediaSendRoute.Capture.TextStory)
    assertThat(viewModel.state.value.selectedMedia).containsExactly(MEDIA)
  }

  @Test
  fun `Given nothing has happened, when created, then the flow is left alone`() = runTest {
    createViewModel()
    advanceUntilIdle()

    assertThat(parentEvents).isEmpty()
  }

  //endregion

  //region Handing off to the flow

  /**
   * Everything the flow, rather than this screen, is responsible for. Kept as one table so that an event added to the
   * screen without a home in the flow's own vocabulary shows up as a gap here.
   */
  @Test
  fun `Given work only the flow can do, when it is asked for, then it is handed over unchanged`() = runTest {
    val handOffs: List<Pair<MediaCaptureScreenEvents, MediaSendFlowEvent>> = listOf(
      MediaCaptureScreenEvents.ShowCamera to MediaSendFlowEvent.NavigateToCamera,
      MediaCaptureScreenEvents.ShowTextStory to MediaSendFlowEvent.NavigateToTextStory,
      MediaCaptureScreenEvents.NextClicked to MediaSendFlowEvent.NavigateToEdit,
      camera(CameraXScreenEvents.GalleryClicked) to MediaSendFlowEvent.NavigateToFolders,
      camera(CameraXScreenEvents.CameraCloseClicked) to MediaSendFlowEvent.CloseRequested,
      camera(CameraXScreenEvents.QrCodeFound("sgnl://example")) to MediaSendFlowEvent.QrCodeScanned("sgnl://example"),
      camera(CameraXScreenEvents.VideoCaptureError) to snackbar(R.string.MediaSendViewModel__error_recording_video)
    )

    handOffs.forEach { (screenEvent, expected) ->
      parentEvents.clear()

      createViewModel().onEvent(screenEvent)
      advanceUntilIdle()

      assertThat(parentEvents, name = screenEvent.toString()).containsExactly(expected)
    }
  }

  @Test
  fun `when an image is captured, then the media it was written to is handed to the flow`() = runTest {
    coEvery { repository.writeCapturedImage(any(), any(), any()) } returns MEDIA

    onCameraEvent(CameraXScreenEvents.ImageCaptured(data = byteArrayOf(1, 2, 3), width = 100, height = 200))

    assertThat(parentEvents).containsExactly(MediaSendFlowEvent.MediaCaptured(MEDIA))
  }

  @Test
  fun `when an image is captured, then it is written out with what the camera reported`() = runTest {
    coEvery { repository.writeCapturedImage(any(), any(), any()) } returns MEDIA
    val data = byteArrayOf(1, 2, 3)

    onCameraEvent(CameraXScreenEvents.ImageCaptured(data = data, width = 640, height = 480))

    coVerify(exactly = 1) { repository.writeCapturedImage(data, 640, 480) }
  }

  @Test
  fun `when an image cannot be written out, then the failure is reported and nothing is handed over`() = runTest {
    coEvery { repository.writeCapturedImage(any(), any(), any()) } returns null

    onCameraEvent(CameraXScreenEvents.ImageCaptured(data = byteArrayOf(1, 2, 3), width = 100, height = 200))

    assertThat(parentEvents).containsExactly(snackbar(R.string.MediaSendViewModel__error_taking_photo))
  }

  /** The duration rides along because it is what the flow drops back to standard quality on. */
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

  //endregion

  /** Raises [event] on a freshly created screen and lets it settle. */
  private fun TestScope.onEvent(event: MediaCaptureScreenEvents) {
    createViewModel().onEvent(event)
    advanceUntilIdle()
  }

  private fun TestScope.onCameraEvent(event: CameraXScreenEvents) {
    onEvent(camera(event))
  }

  private fun camera(event: CameraXScreenEvents) = MediaCaptureScreenEvents.Camera(event)

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
