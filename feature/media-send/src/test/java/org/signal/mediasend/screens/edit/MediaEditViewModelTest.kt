/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit

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
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
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
import org.signal.mediasend.EditorState
import org.signal.mediasend.MediaRecipientId
import org.signal.mediasend.MediaSendDependenciesRule
import org.signal.mediasend.MediaSendFlowEvent
import org.signal.mediasend.MediaSendFlowState
import org.signal.mediasend.R
import org.signal.mediasend.SaveToStorageResult
import org.signal.mediasend.SentMediaQuality
import org.signal.mediasend.SnackbarEvent
import org.signal.mediasend.screens.edit.image.BrushTool
import org.signal.mediasend.screens.edit.video.VideoTrimData
import org.thoughtcrime.securesms.video.TranscodingConfig

/**
 * Covers what the edit screen keeps and what it hands off: the flow's state mirrored into its own, every edit raised as
 * a flow event, and the one job it does itself -- writing the focused image out to shared storage.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class MediaEditViewModelTest {

  @get:Rule
  val mediaSendDependenciesRule = MediaSendDependenciesRule(ApplicationProvider.getApplicationContext())

  private val testDispatcher = StandardTestDispatcher()
  private val repository = mediaSendDependenciesRule.mediaSendRepository
  private val parentEvents = mutableListOf<MediaSendFlowEvent>()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    every { repository.hasDismissedSaveToStorageWarning } returns true
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  //region Parent state

  @Test
  fun `when the flow's selection changes, then the screen's copy of it follows`() = runTest {
    val parentState = MutableStateFlow(MediaSendFlowState())
    val viewModel = createViewModel(parentState)

    parentState.value = MediaSendFlowState(selectedMedia = listOf(MEDIA), focusedMedia = MEDIA)
    advanceUntilIdle()

    assertThat(viewModel.state.value.selectedMedia).containsExactly(MEDIA)
    assertThat(viewModel.state.value.focusedMedia).isEqualTo(MEDIA)
  }

  @Test
  fun `when the flow reports a quality change, then the screen's copy of it follows`() = runTest {
    val parentState = MutableStateFlow(MediaSendFlowState(sentMediaQuality = SentMediaQuality.STANDARD))
    val viewModel = createViewModel(parentState)

    parentState.value = MediaSendFlowState(sentMediaQuality = SentMediaQuality.HIGH)
    advanceUntilIdle()

    assertThat(viewModel.state.value.sentMediaQuality).isEqualTo(SentMediaQuality.HIGH)
  }

  /**
   * The editor reads nearly all of the flow's state, so a field left out of the mapping is a control that silently
   * renders a default. Every field this screen shows is asserted against a parent that differs from the default in all
   * of them.
   */
  @Test
  fun `Given a flow with everything set, when it is reported, then every part the editor shows is mirrored`() = runTest {
    val editorState = EditorState.Image(mockk(relaxed = true))
    val tiers = listOf(mockk<TranscodingConfig.QualityTier>(relaxed = true))
    val parentState = MediaSendFlowState(
      selectedMedia = listOf(MEDIA),
      focusedMedia = MEDIA,
      editorStateMap = mapOf(MEDIA.uri to editorState),
      cameraFirstCapture = MEDIA,
      recipientId = MediaRecipientId(1L),
      message = "hello",
      sentMediaQuality = SentMediaQuality.HIGH,
      videoTranscodingTiers = tiers,
      isStory = false,
      isReply = true,
      isSending = true,
      isTouchEnabled = false,
      isMuteVideoAudioEnabled = true,
      viewOnceToggleState = MediaSendFlowState.ViewOnceToggleState.ONCE
    )

    val state = createViewModel(MutableStateFlow(parentState)).state.value

    assertThat(state).isEqualTo(
      MediaEditState(
        selectedMedia = listOf(MEDIA),
        focusedMedia = MEDIA,
        editorStateMap = mapOf(MEDIA.uri to editorState),
        cameraFirstCapture = MEDIA,
        recipientId = MediaRecipientId(1L),
        message = "hello",
        sentMediaQuality = SentMediaQuality.HIGH,
        videoTranscodingTiers = tiers,
        isStory = false,
        isReply = true,
        isSending = true,
        isTouchEnabled = false,
        isMuteVideoAudioEnabled = true,
        isViewOnceAvailable = true,
        isViewOnceEnabled = true,
        isSavingMedia = false
      )
    )
  }

  @Test
  fun `Given a save in flight, when the flow's state changes, then the save is still reported`() = runTest {
    val parentState = MutableStateFlow(imageState())
    val save = holdSaveOpen()
    val viewModel = createViewModel(parentState)

    viewModel.onEvent(MediaEditScreenEvents.SaveMedia)
    advanceUntilIdle()
    assertThat(viewModel.state.value.isSavingMedia).isTrue()

    parentState.value = imageState().copy(isReply = true)
    advanceUntilIdle()

    assertThat(viewModel.state.value.isSavingMedia).isTrue()
    assertThat(viewModel.state.value.isReply).isTrue()
    save.complete(SaveToStorageResult.SUCCESS)
  }

  //endregion

  //region Handing off to the flow

  /**
   * Everything the flow, rather than this screen, is responsible for: an edit has to survive the editor being swiped
   * away, and a request that leaves the flow was never a screen's to answer. Kept as one table so that an event added
   * to the screen without a home in the flow's own vocabulary shows up as a gap here.
   */
  @Test
  fun `Given work only the flow can do, when it is asked for, then it is handed over unchanged`() = runTest {
    val trimData = VideoTrimData(totalInputDurationUs = 1_000, startTimeUs = 100, endTimeUs = 900)

    val handOffs: List<Pair<MediaEditScreenEvents, MediaSendFlowEvent>> = listOf(
      MediaEditScreenEvents.FocusedMediaChanged(MEDIA) to MediaSendFlowEvent.SetFocusedMedia(MEDIA),
      MediaEditScreenEvents.ReorderSelectedMedia(fromIndex = 2, toIndex = 0) to MediaSendFlowEvent.ReorderSelectedMedia(2, 0),
      MediaEditScreenEvents.RemoveMedia(MEDIA) to MediaSendFlowEvent.RemoveMedia(setOf(MEDIA)),
      MediaEditScreenEvents.SetMediaQuality(SentMediaQuality.HIGH) to MediaSendFlowEvent.SetMediaQuality(SentMediaQuality.HIGH),
      MediaEditScreenEvents.BrushWidthChanged(BrushTool.MARKER, 0.5f) to MediaSendFlowEvent.SetBrushWidth(BrushTool.MARKER, 0.5f),
      MediaEditScreenEvents.ToggleBlurFaces(enabled = true) to MediaSendFlowEvent.SetBlurFacesEnabled(true),
      MediaEditScreenEvents.VideoTrimChanged(trimData, editingComplete = true) to MediaSendFlowEvent.VideoTrimChanged(trimData, editingComplete = true),
      MediaEditScreenEvents.ToggleViewOnce to MediaSendFlowEvent.ToggleViewOnce,
      MediaEditScreenEvents.ToggleVideoMuted to MediaSendFlowEvent.ToggleVideoMuted,
      MediaEditScreenEvents.AddMessageClick(startWithEmojiKeyboard = true) to MediaSendFlowEvent.AddMessageRequested(startWithEmojiKeyboard = true),
      MediaEditScreenEvents.ScheduleSendClick(ScheduleSendOption.PickTime) to MediaSendFlowEvent.ScheduleSendRequested(ScheduleSendOption.PickTime),
      MediaEditScreenEvents.StickerClick to MediaSendFlowEvent.StickerRequested,
      MediaEditScreenEvents.NextClick to MediaSendFlowEvent.NextRequested,
      MediaEditScreenEvents.NavigateToGallery to MediaSendFlowEvent.NavigateToFolders,
      MediaEditScreenEvents.NavigateBack to MediaSendFlowEvent.NavigateBackFromEdit
    )

    handOffs.forEach { (screenEvent, expected) ->
      parentEvents.clear()

      createViewModel().onEvent(screenEvent)
      advanceUntilIdle()

      assertThat(parentEvents, name = screenEvent.toString()).containsExactly(expected)
    }
  }

  //endregion

  //region Saving to storage

  @Test
  fun `when the focused image is saved, then it is written out and confirmed`() = runTest {
    coEvery { repository.saveImageToStorage(any()) } returns SaveToStorageResult.SUCCESS
    val viewModel = createViewModel(MutableStateFlow(imageState()))

    viewModel.onEvent(MediaEditScreenEvents.SaveMedia)
    advanceUntilIdle()

    coVerify(exactly = 1) { repository.saveImageToStorage(any()) }
    assertThat(parentEvents).containsExactly(snackbar(R.string.MediaSendViewModel__media_saved))
    assertThat(viewModel.state.value.isSavingMedia).isFalse()
  }

  @Test
  fun `when a save fails, then the failure is reported`() = runTest {
    coEvery { repository.saveImageToStorage(any()) } returns SaveToStorageResult.FAILURE
    val viewModel = createViewModel(MutableStateFlow(imageState()))

    viewModel.onEvent(MediaEditScreenEvents.SaveMedia)
    advanceUntilIdle()

    assertThat(parentEvents).containsExactly(snackbar(R.string.MediaSendViewModel__error_saving_media))
  }

  @Test
  fun `when a save is refused for lack of access, then that is what is reported`() = runTest {
    coEvery { repository.saveImageToStorage(any()) } returns SaveToStorageResult.NO_WRITE_ACCESS
    val viewModel = createViewModel(MutableStateFlow(imageState()))

    viewModel.onEvent(MediaEditScreenEvents.SaveMedia)
    advanceUntilIdle()

    assertThat(parentEvents).containsExactly(snackbar(R.string.MediaSendViewModel__unable_to_save_without_storage_permission))
  }

  @Test
  fun `Given the focus is not an editable image, when a save is asked for, then nothing is written`() = runTest {
    val documentState = MediaSendFlowState(
      selectedMedia = listOf(MEDIA),
      focusedMedia = MEDIA,
      editorStateMap = mapOf(MEDIA.uri to EditorState.Document(fileName = "report.pdf", fileSize = 1, extension = "pdf"))
    )
    val viewModel = createViewModel(MutableStateFlow(documentState))

    viewModel.onEvent(MediaEditScreenEvents.SaveMedia)
    advanceUntilIdle()

    coVerify(exactly = 0) { repository.saveImageToStorage(any()) }
    assertThat(parentEvents).isEmpty()
  }

  @Test
  fun `Given the warning has not been dismissed, when a save is asked for, then nothing is written until it is confirmed`() = runTest {
    every { repository.hasDismissedSaveToStorageWarning } returns false
    val viewModel = createViewModel(MutableStateFlow(imageState()))

    viewModel.onEvent(MediaEditScreenEvents.SaveMedia)
    advanceUntilIdle()

    coVerify(exactly = 0) { repository.saveImageToStorage(any()) }
    assertThat(viewModel.state.value.isSavingMedia).isFalse()
  }

  @Test
  fun `Given a save in flight, when another is asked for, then it is not written twice`() = runTest {
    val save = holdSaveOpen()
    val viewModel = createViewModel(MutableStateFlow(imageState()))

    viewModel.onEvent(MediaEditScreenEvents.SaveMedia)
    advanceUntilIdle()
    viewModel.onEvent(MediaEditScreenEvents.SaveMedia)
    advanceUntilIdle()

    coVerify(exactly = 1) { repository.saveImageToStorage(any()) }
    save.complete(SaveToStorageResult.SUCCESS)
  }

  @Test
  fun `when the warning is dismissed for good, then that is remembered`() = runTest {
    createViewModel().markSaveToStorageWarningDismissed()

    coVerify(exactly = 1) { repository.markSaveToStorageWarningDismissed() }
  }

  //endregion

  /** Makes the next save suspend until the returned deferred is completed. */
  private fun holdSaveOpen(): CompletableDeferred<SaveToStorageResult> {
    val gate = CompletableDeferred<SaveToStorageResult>()
    coEvery { repository.saveImageToStorage(any()) } coAnswers { gate.await() }
    return gate
  }

  /** Raises [event] on a freshly created screen and lets it settle. */
  private fun TestScope.onEvent(event: MediaEditScreenEvents) {
    createViewModel().onEvent(event)
    advanceUntilIdle()
  }

  private fun snackbar(@StringRes message: Int) = MediaSendFlowEvent.ShowSnackbar(SnackbarEvent(message = message))

  /** A flow whose focused item is an image, which is the only thing saving applies to. */
  private fun imageState() = MediaSendFlowState(
    selectedMedia = listOf(MEDIA),
    focusedMedia = MEDIA,
    editorStateMap = mapOf(MEDIA.uri to EditorState.Image(mockk(relaxed = true)))
  )

  private fun createViewModel(parentState: MutableStateFlow<MediaSendFlowState> = MutableStateFlow(MediaSendFlowState())): MediaEditViewModel {
    return MediaEditViewModel(
      parentState = parentState,
      parentEventEmitter = { parentEvents += it },
      repository = repository
    )
  }

  private companion object {
    private val MEDIA = Media(
      uri = "content://media/1".toUri(),
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
