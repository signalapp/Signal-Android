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
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.doesNotContain
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
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
import org.signal.mediasend.MediaRecipientId
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
  fun `Given a camera-first flow that has yet to pick a destination, when created, then the text story is on offer`() = runTest {
    val viewModel = createViewModel(cameraFirstStoryCapableState())

    assertThat(viewModel.state.value.availableCaptureModes).contains(MediaCaptureMode.TEXT_STORY)
  }

  @Test
  fun `Given a camera-first flow aimed at one recipient's story, when created, then the text story is on offer`() = runTest {
    val viewModel = createViewModel(
      cameraFirstStoryCapableState().copy(mode = MediaSendFlowActivityContract.Mode.SingleRecipient, isStory = true)
    )

    assertThat(viewModel.state.value.availableCaptureModes).contains(MediaCaptureMode.TEXT_STORY)
  }

  @Test
  fun `Given a camera-first flow headed straight to a chat, when created, then the text story is withheld`() = runTest {
    val viewModel = createViewModel(
      cameraFirstStoryCapableState().copy(mode = MediaSendFlowActivityContract.Mode.SingleRecipient, isStory = false)
    )

    assertThat(viewModel.state.value.availableCaptureModes).doesNotContain(MediaCaptureMode.TEXT_STORY)
  }

  @Test
  fun `Given stories are unavailable, when created, then the text story is withheld`() = runTest {
    val viewModel = createViewModel(cameraFirstStoryCapableState().copy(storiesEnabled = false))

    assertThat(viewModel.state.value.availableCaptureModes).doesNotContain(MediaCaptureMode.TEXT_STORY)
  }

  @Test
  fun `Given the camera was not what opened the flow, when created, then the text story is withheld`() = runTest {
    val viewModel = createViewModel(cameraFirstStoryCapableState().copy(isCameraFirst = false))

    assertThat(viewModel.state.value.availableCaptureModes).doesNotContain(MediaCaptureMode.TEXT_STORY)
  }

  /**
   * A text story is text alone, so a flow already carrying a capture has no way to send one. The offer has to be
   * withdrawn as the selection arrives rather than only at construction, since the capture happens on this screen.
   */
  @Test
  fun `Given a camera-first flow, when a capture joins the selection, then the text story is withdrawn`() = runTest {
    val parentState = MutableStateFlow(cameraFirstStoryCapableState())
    val viewModel = createViewModel(parentState)
    assertThat(viewModel.state.value.availableCaptureModes).contains(MediaCaptureMode.TEXT_STORY)

    parentState.value = parentState.value.copy(selectedMedia = listOf(MEDIA))
    advanceUntilIdle()

    assertThat(viewModel.state.value.availableCaptureModes).doesNotContain(MediaCaptureMode.TEXT_STORY)
  }

  @Test
  fun `Given a camera-first flow that already has a selection, when created, then the text story is withheld`() = runTest {
    val viewModel = createViewModel(cameraFirstStoryCapableState().copy(selectedMedia = listOf(MEDIA)))

    assertThat(viewModel.state.value.availableCaptureModes).doesNotContain(MediaCaptureMode.TEXT_STORY)
  }

  /** Emptying the selection puts the flow back where it started, so the text story is on offer again. */
  @Test
  fun `Given a selection that is cleared, when it empties, then the text story is on offer again`() = runTest {
    val parentState = MutableStateFlow(cameraFirstStoryCapableState().copy(selectedMedia = listOf(MEDIA)))
    val viewModel = createViewModel(parentState)

    parentState.value = parentState.value.copy(selectedMedia = emptyList())
    advanceUntilIdle()

    assertThat(viewModel.state.value.availableCaptureModes).contains(MediaCaptureMode.TEXT_STORY)
  }

  /** Recording needs the transcoder, so a device without it has no video mode to offer. */
  @Test
  fun `Given a device that can record, when created, then every mode the flow allows is on offer`() = runTest {
    val viewModel = createViewModel(cameraFirstStoryCapableState())

    assertThat(viewModel.state.value.availableCaptureModes)
      .containsExactly(MediaCaptureMode.VIDEO, MediaCaptureMode.PHOTO, MediaCaptureMode.TEXT_STORY)
  }

  @Test
  @Config(sdk = [25])
  fun `Given a device that cannot record, when created, then video is withheld`() = runTest {
    val viewModel = createViewModel(cameraFirstStoryCapableState())

    assertThat(viewModel.state.value.availableCaptureModes)
      .containsExactly(MediaCaptureMode.PHOTO, MediaCaptureMode.TEXT_STORY)
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
   * the flow does report something while the text story editor is open.
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
  fun `when a camera mode is picked, then it becomes the selected mode`() = runTest {
    val viewModel = createViewModel()

    viewModel.onEvent(MediaCaptureScreenEvents.CaptureModeSelected(MediaCaptureMode.VIDEO))
    advanceUntilIdle()

    assertThat(viewModel.state.value.selectedCaptureMode).isEqualTo(MediaCaptureMode.VIDEO)
  }

  /**
   * The text story is a screen of its own rather than a mode of the camera, so navigation reports it as showing and the
   * camera mode survives the trip.
   */
  @Test
  fun `Given a camera mode was picked, when the text story opens and closes, then the camera mode is still selected`() = runTest {
    val viewModel = createViewModel()
    viewModel.onEvent(MediaCaptureScreenEvents.CaptureModeSelected(MediaCaptureMode.VIDEO))

    viewModel.onEvent(MediaCaptureScreenEvents.SelectedCaptureScreenChanged(MediaSendRoute.Capture.TextStory))
    advanceUntilIdle()
    assertThat(viewModel.state.value.selectedCaptureMode).isEqualTo(MediaCaptureMode.TEXT_STORY)

    viewModel.onEvent(MediaCaptureScreenEvents.SelectedCaptureScreenChanged(MediaSendRoute.Capture.Camera))
    advanceUntilIdle()
    assertThat(viewModel.state.value.selectedCaptureMode).isEqualTo(MediaCaptureMode.VIDEO)
  }

  /** A running recording has the screen to itself, so there is no point offering the mode bar. */
  @Test
  fun `Given a recording is running, when reported, then the mode bar is withheld`() = runTest {
    val viewModel = createViewModel(cameraFirstStoryCapableState())

    viewModel.onEvent(camera(CameraXScreenEvents.RecordingStateChanged(isRecording = true)))
    advanceUntilIdle()

    assertThat(viewModel.state.value.isRecording).isTrue()
    assertThat(viewModel.state.value.canDisplayModeBar).isFalse()
  }

  @Test
  fun `Given a recording that has finished, when reported, then the mode bar is back`() = runTest {
    val viewModel = createViewModel(cameraFirstStoryCapableState())
    viewModel.onEvent(camera(CameraXScreenEvents.RecordingStateChanged(isRecording = true)))

    viewModel.onEvent(camera(CameraXScreenEvents.RecordingStateChanged(isRecording = false)))
    advanceUntilIdle()

    assertThat(viewModel.state.value.canDisplayModeBar).isTrue()
  }

  //region The button for moving on

  @Test
  fun `Given nothing has been captured, when created, then there is nothing to move on with`() = runTest {
    val viewModel = createViewModel(cameraFirstStoryCapableState())

    assertThat(viewModel.state.value.canDisplayNextButton).isFalse()
  }

  @Test
  fun `Given a camera-first flow, when a capture joins the selection, then there is something to move on with`() = runTest {
    val parentState = MutableStateFlow(cameraFirstStoryCapableState())
    val viewModel = createViewModel(parentState)

    parentState.value = parentState.value.copy(selectedMedia = listOf(MEDIA))
    advanceUntilIdle()

    assertThat(viewModel.state.value.canDisplayNextButton).isTrue()
  }

  @Test
  fun `Given a selection, when a recording starts, then moving on is withheld until it finishes`() = runTest {
    val viewModel = createViewModel(cameraFirstStoryCapableState().copy(selectedMedia = listOf(MEDIA)))

    viewModel.onEvent(camera(CameraXScreenEvents.RecordingStateChanged(isRecording = true)))
    advanceUntilIdle()

    assertThat(viewModel.state.value.canDisplayNextButton).isFalse()

    viewModel.onEvent(camera(CameraXScreenEvents.RecordingStateChanged(isRecording = false)))
    advanceUntilIdle()

    assertThat(viewModel.state.value.canDisplayNextButton).isTrue()
  }

  /** Only the chrome's tint reads it, but it still has to arrive for there to be anything to tint with. */
  @Test
  fun `Given a flow headed to one recipient, when created, then that recipient is carried through`() = runTest {
    val viewModel = createViewModel(cameraFirstStoryCapableState().copy(recipientId = RECIPIENT_ID))

    assertThat(viewModel.state.value.recipientId).isEqualTo(RECIPIENT_ID)
  }

  @Test
  fun `Given a flow with no destination yet, when created, then there is no recipient to tint with`() = runTest {
    val viewModel = createViewModel(cameraFirstStoryCapableState())

    assertThat(viewModel.state.value.recipientId).isNull()
  }

  //endregion

  /** A recording is the camera's own business, so the flow around it is not told. */
  @Test
  fun `Given a recording is running, when reported, then the flow is left alone`() = runTest {
    createViewModel().onEvent(camera(CameraXScreenEvents.RecordingStateChanged(isRecording = true)))
    advanceUntilIdle()

    assertThat(parentEvents).isEmpty()
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
   * Everything the flow, rather than this screen, is responsible for. Kept as one table so an event added to the screen
   * with no counterpart in the flow's vocabulary shows up as a gap here.
   */
  @Test
  fun `Given work only the flow can do, when it is asked for, then it is handed over unchanged`() = runTest {
    val handOffs: List<Pair<MediaCaptureScreenEvents, MediaSendFlowEvent>> = listOf(
      MediaCaptureScreenEvents.CaptureModeSelected(MediaCaptureMode.PHOTO) to MediaSendFlowEvent.NavigateToCamera,
      MediaCaptureScreenEvents.CaptureModeSelected(MediaCaptureMode.VIDEO) to MediaSendFlowEvent.NavigateToCamera,
      MediaCaptureScreenEvents.CaptureModeSelected(MediaCaptureMode.TEXT_STORY) to MediaSendFlowEvent.NavigateToTextStory,
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

  /** The duration comes along because the flow uses it to decide whether to drop to standard quality. */
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
    private val RECIPIENT_ID = MediaRecipientId(id = 7L)

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
