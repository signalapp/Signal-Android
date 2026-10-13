/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit.video.trim

import android.app.Application
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.core.net.toUri
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.mediasend.screens.edit.video.VideoTrimData

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class VideoTrimBarPresenterTest {

  private val testDispatcher = UnconfinedTestDispatcher()
  private val emittedActions = mutableListOf<VideoTrimBarAction>()
  private val thumbnailFlows = mutableMapOf<Uri, MutableSharedFlow<VideoThumbnailResult>>()

  @Before
  fun setUp() {
    mockkObject(VideoThumbnailRepository)
    every { VideoThumbnailRepository.thumbnails(any(), any(), any()) } answers { thumbnailsFor(firstArg()) }
  }

  @After
  fun tearDown() {
    unmockkObject(VideoThumbnailRepository)
  }

  //region Host input

  @Test
  fun `Given a trimmed video, when the host reports it, then the bar shows that trim`() = runTest(testDispatcher) {
    val presenter = createPresenter()

    presenter.showVideo(VIDEO_A, trim(startUs = 1_000_000, endUs = 4_000_000), maxDurationUs = 5_000_000)

    val state = presenter.state.value
    assertThat(state.durationUs).isEqualTo(DURATION_US)
    assertThat(state.startUs).isEqualTo(1_000_000L)
    assertThat(state.endUs).isEqualTo(4_000_000L)
    assertThat(state.maxRangeUs).isEqualTo(5_000_000L)
    assertThat(state.isTrimmed).isTrue()
  }

  @Test
  fun `Given another video is showing, when the host reports a trim for the old one, then it is ignored`() = runTest(testDispatcher) {
    val presenter = createPresenter()
    presenter.showVideo(VIDEO_A, trim(startUs = 0, endUs = DURATION_US))
    presenter.showVideo(VIDEO_B, trim(startUs = 0, endUs = DURATION_US))

    presenter.onEvent(VideoTrimBarEvents.TrimDataChanged(VIDEO_A, trim(startUs = 2_000_000, endUs = 3_000_000), maxDurationUs = null))

    assertThat(presenter.state.value.startUs).isEqualTo(0L)
    assertThat(presenter.state.value.endUs).isEqualTo(DURATION_US)
  }

  @Test
  fun `Given another video is showing, when the old one's player reports its position, then it is ignored`() = runTest(testDispatcher) {
    val presenter = createPresenter()
    presenter.showVideo(VIDEO_A, trim(startUs = 0, endUs = DURATION_US))
    presenter.showVideo(VIDEO_B, trim(startUs = 0, endUs = DURATION_US))

    presenter.onEvent(VideoTrimBarEvents.PlaybackPositionChanged(VIDEO_A, 7_000_000))

    assertThat(presenter.state.value.playheadUs).isEqualTo(0L)
  }

  @Test
  fun `Given a drag in progress, when the host reports a trim or position, then the drag is not disturbed`() = runTest(testDispatcher) {
    val presenter = createPresenter()
    presenter.showVideo(VIDEO_A, trim(startUs = 0, endUs = DURATION_US))
    presenter.onEvent(VideoTrimBarEvents.DragStarted(TrimDragTarget.END))
    presenter.onEvent(VideoTrimBarEvents.HandleDragged(TrimDragTarget.END, 6_000_000))

    presenter.onEvent(VideoTrimBarEvents.TrimDataChanged(VIDEO_A, trim(startUs = 0, endUs = 9_000_000), maxDurationUs = null))
    presenter.onEvent(VideoTrimBarEvents.PlaybackPositionChanged(VIDEO_A, 3_000_000))

    assertThat(presenter.state.value.endUs).isEqualTo(6_000_000L)
    assertThat(presenter.state.value.playheadUs).isEqualTo(0L)
  }

  //endregion

  //region Dragging

  @Test
  fun `when a handle is dragged and released, then each move is reported and the last completes the edit`() = runTest(testDispatcher) {
    val presenter = createPresenter()
    presenter.showVideo(VIDEO_A, trim(startUs = 0, endUs = DURATION_US))

    presenter.onEvent(VideoTrimBarEvents.DragStarted(TrimDragTarget.END))
    presenter.onEvent(VideoTrimBarEvents.HandleDragged(TrimDragTarget.END, 8_000_000))
    presenter.onEvent(VideoTrimBarEvents.HandleDragged(TrimDragTarget.END, 6_000_000))
    presenter.onEvent(VideoTrimBarEvents.DragEnded)

    val trimmed = VideoTrimData(isDurationEdited = true, totalInputDurationUs = DURATION_US, startTimeUs = 0, endTimeUs = 6_000_000)
    assertThat(emittedActions).containsExactly(
      VideoTrimBarAction.TrimChanged(VIDEO_A, trimmed.copy(endTimeUs = 8_000_000), editingComplete = false),
      VideoTrimBarAction.TrimChanged(VIDEO_A, trimmed, editingComplete = false),
      VideoTrimBarAction.TrimChanged(VIDEO_A, trimmed, editingComplete = true)
    )
    assertThat(presenter.state.value.isDragging).isFalse()
  }

  @Test
  fun `when the start handle is dragged past the end, then the range stops at its minimum`() = runTest(testDispatcher) {
    val presenter = createPresenter()
    presenter.showVideo(VIDEO_A, trim(startUs = 0, endUs = 4_000_000))

    presenter.onEvent(VideoTrimBarEvents.DragStarted(TrimDragTarget.START))
    presenter.onEvent(VideoTrimBarEvents.HandleDragged(TrimDragTarget.START, 9_000_000))

    assertThat(presenter.state.value.startUs).isEqualTo(4_000_000L - VideoTrimBarPresenter.MINIMUM_RANGE_US)
  }

  @Test
  fun `Given a limit, when the end handle is dragged past it, then the start is pulled along`() = runTest(testDispatcher) {
    val presenter = createPresenter()
    presenter.showVideo(VIDEO_A, trim(startUs = 0, endUs = 3_000_000), maxDurationUs = 3_000_000)

    presenter.onEvent(VideoTrimBarEvents.DragStarted(TrimDragTarget.END))
    presenter.onEvent(VideoTrimBarEvents.HandleDragged(TrimDragTarget.END, 9_000_000))

    assertThat(presenter.state.value.startUs).isEqualTo(6_000_000L)
    assertThat(presenter.state.value.endUs).isEqualTo(9_000_000L)
  }

  @Test
  fun `Given a limit, when the start handle is dragged back past it, then the end is pulled along`() = runTest(testDispatcher) {
    val presenter = createPresenter()
    presenter.showVideo(VIDEO_A, trim(startUs = 6_000_000, endUs = 9_000_000), maxDurationUs = 3_000_000)

    presenter.onEvent(VideoTrimBarEvents.DragStarted(TrimDragTarget.START))
    presenter.onEvent(VideoTrimBarEvents.HandleDragged(TrimDragTarget.START, 1_000_000))

    assertThat(presenter.state.value.startUs).isEqualTo(1_000_000L)
    assertThat(presenter.state.value.endUs).isEqualTo(4_000_000L)
  }

  @Test
  fun `when the playhead is scrubbed, then it stays inside the selection and the seeks are reported`() = runTest(testDispatcher) {
    val presenter = createPresenter()
    presenter.showVideo(VIDEO_A, trim(startUs = 2_000_000, endUs = 5_000_000))

    presenter.onEvent(VideoTrimBarEvents.DragStarted(TrimDragTarget.PLAYHEAD))
    presenter.onEvent(VideoTrimBarEvents.PlayheadDragged(3_000_000))
    presenter.onEvent(VideoTrimBarEvents.PlayheadDragged(9_000_000))
    presenter.onEvent(VideoTrimBarEvents.DragEnded)

    assertThat(emittedActions).containsExactly(
      VideoTrimBarAction.Seek(VIDEO_A, 3_000_000, editingComplete = false),
      VideoTrimBarAction.Seek(VIDEO_A, 5_000_000, editingComplete = false),
      VideoTrimBarAction.Seek(VIDEO_A, 5_000_000, editingComplete = true)
    )
  }

  @Test
  fun `when the strip is tapped, then the playhead moves inside the selection and a finished seek is reported`() = runTest(testDispatcher) {
    val presenter = createPresenter()
    presenter.showVideo(VIDEO_A, trim(startUs = 2_000_000, endUs = 5_000_000))

    presenter.onEvent(VideoTrimBarEvents.PlayheadTapped(3_000_000))
    presenter.onEvent(VideoTrimBarEvents.PlayheadTapped(9_000_000))

    assertThat(presenter.state.value.playheadUs).isEqualTo(5_000_000L)
    assertThat(emittedActions).containsExactly(
      VideoTrimBarAction.Seek(VIDEO_A, 3_000_000, editingComplete = true),
      VideoTrimBarAction.Seek(VIDEO_A, 5_000_000, editingComplete = true)
    )
  }

  @Test
  fun `Given the duration is unknown, when the strip is tapped, then nothing is reported`() = runTest(testDispatcher) {
    val presenter = createPresenter()
    presenter.onEvent(VideoTrimBarEvents.SourceChanged(VIDEO_A))

    presenter.onEvent(VideoTrimBarEvents.PlayheadTapped(3_000_000))

    assertThat(emittedActions).isEmpty()
  }

  @Test
  fun `Given the duration is unknown, when a drag starts, then nothing is dragged`() = runTest(testDispatcher) {
    val presenter = createPresenter()
    presenter.onEvent(VideoTrimBarEvents.SourceChanged(VIDEO_A))

    presenter.onEvent(VideoTrimBarEvents.DragStarted(TrimDragTarget.END))
    presenter.onEvent(VideoTrimBarEvents.HandleDragged(TrimDragTarget.END, 1_000_000))
    presenter.onEvent(VideoTrimBarEvents.DragEnded)

    assertThat(presenter.state.value.activeDrag).isNull()
    assertThat(emittedActions).isEmpty()
  }

  @Test
  fun `Given a drag in progress, when the video changes, then the drag is finished for the old video`() = runTest(testDispatcher) {
    val presenter = createPresenter()
    presenter.showVideo(VIDEO_A, trim(startUs = 0, endUs = DURATION_US))
    presenter.onEvent(VideoTrimBarEvents.DragStarted(TrimDragTarget.END))
    presenter.onEvent(VideoTrimBarEvents.HandleDragged(TrimDragTarget.END, 6_000_000))

    presenter.onEvent(VideoTrimBarEvents.SourceChanged(VIDEO_B))
    presenter.onEvent(VideoTrimBarEvents.HandleDragged(TrimDragTarget.END, 2_000_000))
    presenter.onEvent(VideoTrimBarEvents.DragEnded)

    assertThat(emittedActions.last()).isEqualTo(
      VideoTrimBarAction.TrimChanged(
        VIDEO_A,
        VideoTrimData(isDurationEdited = true, totalInputDurationUs = DURATION_US, startTimeUs = 0, endTimeUs = 6_000_000),
        editingComplete = true
      )
    )
    assertThat(presenter.state.value.isDragging).isFalse()
  }

  //endregion

  //region Thumbnails

  @Test
  fun `Given the host has no trim yet, when the duration is read, then the full range is reported once`() = runTest(testDispatcher) {
    val presenter = createPresenter()
    presenter.onEvent(VideoTrimBarEvents.SourceChanged(VIDEO_A))
    presenter.onEvent(VideoTrimBarEvents.TrimDataChanged(VIDEO_A, VideoTrimData(), maxDurationUs = null))
    presenter.onEvent(VideoTrimBarEvents.StripMeasured(widthPx = 340, heightPx = 40))

    thumbnailsFor(VIDEO_A).emit(VideoThumbnailResult.DurationKnown(DURATION_US))
    presenter.onEvent(VideoTrimBarEvents.TrimDataChanged(VIDEO_A, VideoTrimData(), maxDurationUs = null))

    assertThat(emittedActions).containsExactly(
      VideoTrimBarAction.TrimChanged(
        VIDEO_A,
        VideoTrimData(isDurationEdited = false, totalInputDurationUs = DURATION_US, startTimeUs = 0, endTimeUs = DURATION_US),
        editingComplete = true
      )
    )
  }

  @Test
  fun `when the duration is read before any thumbnail, then the handles can already be dragged`() = runTest(testDispatcher) {
    val presenter = createPresenter()
    presenter.onEvent(VideoTrimBarEvents.SourceChanged(VIDEO_A))
    presenter.onEvent(VideoTrimBarEvents.StripMeasured(widthPx = 340, heightPx = 40))

    thumbnailsFor(VIDEO_A).emit(VideoThumbnailResult.DurationKnown(DURATION_US))
    presenter.onEvent(VideoTrimBarEvents.DragStarted(TrimDragTarget.END))

    assertThat(presenter.state.value.thumbnails).isEqualTo(List(9) { null })
    assertThat(presenter.state.value.activeDrag).isEqualTo(TrimDragTarget.END)
  }

  @Test
  fun `when a thumbnail is decoded, then it fills its slot`() = runTest(testDispatcher) {
    val presenter = createPresenter()
    val bitmap = mockk<ImageBitmap>()
    presenter.onEvent(VideoTrimBarEvents.SourceChanged(VIDEO_A))
    presenter.onEvent(VideoTrimBarEvents.StripMeasured(widthPx = 120, heightPx = 40))

    thumbnailsFor(VIDEO_A).emit(VideoThumbnailResult.Thumbnail(1, bitmap))

    assertThat(presenter.state.value.thumbnails).containsExactly(null, bitmap, null)
  }

  @Test
  fun `Given the video changed, when the old video's decode reports, then nothing lands on the new one`() = runTest(testDispatcher) {
    val presenter = createPresenter()
    presenter.onEvent(VideoTrimBarEvents.StripMeasured(widthPx = 120, heightPx = 40))
    presenter.onEvent(VideoTrimBarEvents.SourceChanged(VIDEO_A))
    val oldDecode = thumbnailsFor(VIDEO_A)

    presenter.onEvent(VideoTrimBarEvents.SourceChanged(VIDEO_B))
    oldDecode.emit(VideoThumbnailResult.DurationKnown(DURATION_US))
    oldDecode.emit(VideoThumbnailResult.Thumbnail(0, mockk()))

    assertThat(presenter.state.value.durationUs).isNull()
    assertThat(presenter.state.value.thumbnails).containsExactly(null, null, null)
  }

  //endregion

  /** Points the presenter at [uri] with the host's [trimData], as the edit screen does. */
  private fun VideoTrimBarPresenter.showVideo(uri: Uri, trimData: VideoTrimData, maxDurationUs: Long? = null) {
    onEvent(VideoTrimBarEvents.SourceChanged(uri))
    onEvent(VideoTrimBarEvents.TrimDataChanged(uri, trimData, maxDurationUs))
  }

  private fun trim(startUs: Long, endUs: Long) = VideoTrimData(
    isDurationEdited = startUs > 0 || endUs < DURATION_US,
    totalInputDurationUs = DURATION_US,
    startTimeUs = startUs,
    endTimeUs = endUs
  )

  private fun thumbnailsFor(uri: Uri): MutableSharedFlow<VideoThumbnailResult> = thumbnailFlows.getOrPut(uri) { MutableSharedFlow() }

  private fun TestScope.createPresenter(): VideoTrimBarPresenter {
    val presenter = VideoTrimBarPresenter(backgroundScope)

    presenter
      .actions
      .onEach { emittedActions += it }
      .launchIn(backgroundScope)

    return presenter
  }

  private companion object {
    const val DURATION_US = 10_000_000L
    val VIDEO_A: Uri = "content://media/video/a".toUri()
    val VIDEO_B: Uri = "content://media/video/b".toUri()
  }
}
