/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit

import android.app.Application
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.media.Media
import org.signal.core.ui.CoreUiDependenciesRule
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.util.ContentTypeUtil
import org.signal.mediasend.EditorState
import org.signal.mediasend.MediaSendDependenciesRule
import org.signal.mediasend.test.TestTags

/**
 * Covers which of the shared toolbar actions each kind of media gets, which is the parity contract with the v2 review
 * screen: send quality is meaningless for stories and documents, saving is for stills only, and adding media is off the
 * table for documents and view-once sends.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MediaEditorToolbarSharedButtonsTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  @get:Rule
  val mediaSendDependenciesRule = MediaSendDependenciesRule(ApplicationProvider.getApplicationContext())

  @Test
  fun `Given an image, when rendering the toolbar, then quality, save and add media are offered`() {
    setContent(state(media = IMAGE), IMAGE_EDITOR_STATE)

    assertQuality(visible = true)
    assertSave(visible = true)
    assertAddMedia(visible = true)
  }

  @Test
  fun `Given a gif, when rendering the toolbar, then quality, save and add media are offered`() {
    setContent(state(media = GIF), EditorState.Gif)

    assertQuality(visible = true)
    assertSave(visible = true)
    assertAddMedia(visible = true)
  }

  @Test
  fun `Given a video, when rendering the toolbar, then saving is not offered`() {
    setContent(state(media = VIDEO), VIDEO_EDITOR_STATE)

    assertQuality(visible = true)
    assertSave(visible = false)
    assertAddMedia(visible = true)
  }

  @Test
  fun `Given a video gif, when rendering the toolbar, then saving is not offered`() {
    setContent(state(media = VIDEO_GIF), EditorState.VideoGif)

    assertQuality(visible = true)
    assertSave(visible = false)
    assertAddMedia(visible = true)
  }

  @Test
  fun `Given a document, when rendering the toolbar, then nothing is offered`() {
    setContent(state(media = DOCUMENT), DOCUMENT_EDITOR_STATE)

    assertQuality(visible = false)
    assertSave(visible = false)
    assertAddMedia(visible = false)
  }

  @Test
  fun `Given a story, when rendering the toolbar, then quality is not offered`() {
    setContent(state(media = IMAGE, isStory = true), IMAGE_EDITOR_STATE)

    assertQuality(visible = false)
    assertSave(visible = true)
    assertAddMedia(visible = true)
  }

  @Test
  fun `Given view once is on, when rendering the toolbar, then adding media is not offered`() {
    setContent(state(media = IMAGE, viewOnce = true), IMAGE_EDITOR_STATE)

    assertQuality(visible = true)
    assertSave(visible = true)
    assertAddMedia(visible = false)
  }

  @Test
  fun `Given the mute labs flag is off, when rendering the toolbar for a video, then muting is not offered`() {
    setContent(state(media = VIDEO), VIDEO_EDITOR_STATE)

    assertMute(visible = false)
  }

  @Test
  fun `Given the mute labs flag is on, when rendering the toolbar for a video, then muting is offered`() {
    setContent(state(media = VIDEO, muteEnabled = true), VIDEO_EDITOR_STATE)

    assertMute(visible = true)
  }

  @Test
  fun `Given the mute labs flag is on, when rendering the toolbar for a video gif, then muting is not offered`() {
    setContent(state(media = VIDEO_GIF, muteEnabled = true), EditorState.VideoGif)

    assertMute(visible = false)
  }

  @Test
  fun `Given the mute labs flag is on, when rendering the toolbar for an image, then muting is not offered`() {
    setContent(state(media = IMAGE, muteEnabled = true), IMAGE_EDITOR_STATE)

    assertMute(visible = false)
  }

  /**
   * The selection rail is the multi-item form of the add media button, so it follows the same rule.
   */
  @Test
  fun `Given a document or a view once send, then the selection rail is hidden`() {
    assertFalse(isAddMediaVisible(state(media = DOCUMENT), DOCUMENT_EDITOR_STATE))
    assertFalse(isAddMediaVisible(state(media = IMAGE, viewOnce = true), IMAGE_EDITOR_STATE))
    assertTrue(isAddMediaVisible(state(media = IMAGE), IMAGE_EDITOR_STATE))
  }

  /**
   * An empty toolbar still claims its slot on the edit screen, so the document page has to be able to tell that nothing
   * would render.
   */
  @Test
  fun `Given a document, then the toolbar reports that it has nothing to show`() {
    assertFalse(hasSharedToolbarButtons(state(media = DOCUMENT), DOCUMENT_EDITOR_STATE))
    assertTrue(hasSharedToolbarButtons(state(media = VIDEO, isStory = true), EditorState.VideoGif))
  }

  private fun assertQuality(visible: Boolean) = assertTag(TestTags.MEDIA_EDITOR_TOOLBAR_QUALITY_BUTTON, visible)

  private fun assertSave(visible: Boolean) = assertTag(TestTags.MEDIA_EDITOR_TOOLBAR_SAVE_BUTTON, visible)

  private fun assertAddMedia(visible: Boolean) = assertTag(TestTags.MEDIA_EDITOR_TOOLBAR_ADD_MEDIA_BUTTON, visible)

  private fun assertMute(visible: Boolean) = assertTag(TestTags.MEDIA_EDITOR_TOOLBAR_MUTE_BUTTON, visible)

  private fun assertTag(tag: String, visible: Boolean) {
    val node = composeTestRule.onNodeWithTag(tag)
    if (visible) {
      node.assertIsDisplayed()
    } else {
      node.assertDoesNotExist()
    }
  }

  private fun setContent(state: MediaEditState, editorState: EditorState) {
    composeTestRule.setContent {
      SignalTheme {
        MediaEditorToolbar {
          MediaEditorToolbarSharedButtons(
            state = state,
            editorState = editorState,
            onEvent = {}
          )
        }
      }
    }
  }

  private fun state(media: Media, isStory: Boolean = false, viewOnce: Boolean = false, muteEnabled: Boolean = false): MediaEditState {
    return MediaEditState(
      selectedMedia = listOf(media),
      focusedMedia = media,
      isStory = isStory,
      isViewOnceAvailable = !isStory,
      isViewOnceEnabled = viewOnce,
      isMuteVideoAudioEnabled = muteEnabled
    )
  }

  companion object {
    /** The editor model is never read by the toolbar, and a real one cannot be built under Robolectric's legacy graphics. */
    private val IMAGE_EDITOR_STATE = EditorState.Image(mockk(relaxed = true))
    private val DOCUMENT_EDITOR_STATE = EditorState.Document(fileName = "report.pdf", fileSize = 1, extension = "pdf")
    private val VIDEO_EDITOR_STATE = EditorState.VideoTrim.forVideo(durationUs = 1_000, maxDurationUs = 1_000)

    private val IMAGE = media(contentType = ContentTypeUtil.IMAGE_JPEG)
    private val GIF = media(contentType = ContentTypeUtil.IMAGE_GIF)
    private val VIDEO = media(contentType = ContentTypeUtil.VIDEO_MP4)
    private val VIDEO_GIF = media(contentType = ContentTypeUtil.VIDEO_MP4, isVideoGif = true)
    private val DOCUMENT = media(contentType = "application/pdf")

    private fun media(contentType: String, isVideoGif: Boolean = false): Media {
      return Media(
        uri = Uri.parse("content://media/$contentType"),
        contentType = contentType,
        date = 0,
        width = 0,
        height = 0,
        size = 1,
        duration = 0,
        isBorderless = false,
        isVideoGif = isVideoGif,
        bucketId = Media.ALL_MEDIA_BUCKET_ID,
        caption = null,
        transformProperties = null,
        fileName = null
      )
    }
  }
}
