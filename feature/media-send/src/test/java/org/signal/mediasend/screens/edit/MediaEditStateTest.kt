/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit

import android.app.Application
import androidx.core.net.toUri
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.media.Media
import org.signal.mediasend.EditorState
import org.signal.mediasend.screens.edit.video.VideoTrimData

/**
 * Covers what the editor reads off its state rather than off a control: whether backing out discards a capture, and
 * which editor the focused item gets.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class MediaEditStateTest {

  @Test
  fun `Given a capture is all that is selected, when backing out, then it is the capture that would be discarded`() {
    val state = MediaEditState(selectedMedia = listOf(CAPTURE), cameraFirstCapture = CAPTURE)

    assertThat(state.isOnlyCameraFirstCapture).isTrue()
  }

  /** Something was picked alongside the capture, so backing out is an ordinary step back up the stack. */
  @Test
  fun `Given a capture with something else selected, when backing out, then nothing would be discarded`() {
    val state = MediaEditState(selectedMedia = listOf(CAPTURE, GALLERY_ITEM), cameraFirstCapture = CAPTURE)

    assertThat(state.isOnlyCameraFirstCapture).isFalse()
  }

  @Test
  fun `Given a selection that was never captured, when backing out, then nothing would be discarded`() {
    val state = MediaEditState(selectedMedia = listOf(GALLERY_ITEM))

    assertThat(state.isOnlyCameraFirstCapture).isFalse()
  }

  /**
   * Population replaces a capture with a new, non-equal [Media] for the same URI, so a stale capture must not read as
   * the thing on screen.
   */
  @Test
  fun `Given a capture that is no longer what is selected, when backing out, then nothing would be discarded`() {
    val state = MediaEditState(selectedMedia = listOf(GALLERY_ITEM), cameraFirstCapture = CAPTURE)

    assertThat(state.isOnlyCameraFirstCapture).isFalse()
  }

  @Test
  fun `Given a focused item, when its editor is asked for, then the one keyed to its uri comes back`() {
    val editorState = EditorState.Image(mockk(relaxed = true))
    val state = MediaEditState(
      selectedMedia = listOf(CAPTURE, GALLERY_ITEM),
      focusedMedia = GALLERY_ITEM,
      editorStateMap = mapOf(CAPTURE.uri to EditorState.Gif, GALLERY_ITEM.uri to editorState)
    )

    assertThat(state.focusedEditorState).isEqualTo(editorState)
  }

  @Test
  fun `Given nothing is focused, when an editor is asked for, then there is none`() {
    assertThat(MediaEditState(selectedMedia = listOf(CAPTURE)).focusedEditorState).isNull()
  }

  @Test
  fun `Given a trimmed video, when its trim is asked for, then the trim it was given comes back`() {
    val trimData = VideoTrimData(totalInputDurationUs = 1_000, startTimeUs = 100, endTimeUs = 900)
    val state = MediaEditState(editorStateMap = mapOf(CAPTURE.uri to EditorState.VideoTrim(trimData)))

    assertThat(state.getOrCreateVideoTrimData(CAPTURE.uri)).isEqualTo(trimData)
  }

  /** The trim bar composes before the editor state for a page exists, so an untrimmed video needs a default. */
  @Test
  fun `Given an item with no trim yet, when its trim is asked for, then an untrimmed default comes back`() {
    assertThat(MediaEditState().getOrCreateVideoTrimData(CAPTURE.uri)).isEqualTo(VideoTrimData())
  }

  private companion object {
    private val CAPTURE = media("content://capture")
    private val GALLERY_ITEM = media("content://gallery/1")

    private fun media(uri: String) = Media(
      uri = uri.toUri(),
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
