/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediaviewer

import android.app.Application
import android.net.Uri
import android.os.Looper
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.contains
import io.mockk.every
import io.mockk.mockk
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.database.AttachmentId
import org.signal.emoji.EmojiDependencies
import org.signal.glide.SignalGlideDependencies
import org.signal.glide.common.io.InputStreamFactory
import org.signal.mediaviewer.playback.MediaViewerPlaybackController
import org.signal.mediaviewer.playback.PlaybackUiState
import org.signal.uicomponents.styledtext.StyleRange
import org.signal.uicomponents.styledtext.StyledString
import org.signal.video.VideoPlayer
import org.signal.video.exo.ExoPlayerPool
import java.io.ByteArrayInputStream
import java.io.InputStream

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MediaViewerScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val events = mutableListOf<MediaViewerEvent>()

  @Before
  fun setUp() {
    // Signal's emoji path blocks on EmojiSource.refresh(), which never runs under Robolectric.
    EmojiDependencies.init(
      ApplicationProvider.getApplicationContext(),
      mockk(relaxed = true) {
        every { providePreferSystemEmoji() } returns true
      }
    )

    // PlayerView rejects a player whose application looper is not the main looper, so the pooled
    // ExoPlayer mock must report the real main looper for the video pages to bind.
    val exoPlayer = mockk<ExoPlayer>(relaxed = true) {
      every { applicationLooper } returns Looper.getMainLooper()
    }
    val exoPlayerPool = mockk<ExoPlayerPool<ExoPlayer>>(relaxed = true) {
      every { require(any()) } returns exoPlayer
    }
    MediaViewerDependencies.init(
      object : MediaViewerDependencies.Provider {
        override fun provideExoPlayerPool(): ExoPlayerPool<ExoPlayer> = exoPlayerPool
      }
    )
    SignalGlideDependencies.init(
      ApplicationProvider.getApplicationContext(),
      object : SignalGlideDependencies.Provider {
        override fun getUriInputStreamFactory(uri: Uri, thumbnailTimeUs: Long): InputStreamFactory {
          return object : InputStreamFactory {
            override fun create(): InputStream = ByteArrayInputStream(ByteArray(0))
          }
        }

        override fun isLocalUri(uri: Uri): Boolean = false
      }
    )
  }

  @Test
  fun givenAnImage_whenITapBack_thenIExpectBackClicked() {
    setContent(createState())

    composeTestRule.onNodeWithTag(MediaViewerScreenTestTags.BACK_BUTTON).performClick()

    assertThat(events).contains(MediaViewerEvent.BackClicked)
  }

  @Test
  fun givenAMessage_whenITapTheTitle_thenIExpectToolbarTitleClicked() {
    setContent(createState())

    composeTestRule.onNodeWithTag(MediaViewerScreenTestTags.TOOLBAR_TITLE).performClick()

    assertThat(events).contains(MediaViewerEvent.ToolbarTitleClicked)
  }

  @Test
  fun givenAMessage_thenIExpectTheTitleToAnnounceTheSenderAndDate() {
    setContent(createState())

    composeTestRule.onNodeWithContentDescription("Miles one hour ago").assertExists()
  }

  @Test
  fun givenTheOverflowMenu_whenITapEach_thenIExpectMenuEvents() {
    setContent(createState())

    composeTestRule.onNodeWithTag(MediaViewerScreenTestTags.OVERFLOW_BUTTON).performClick()
    composeTestRule.onNodeWithTag(MediaViewerScreenTestTags.MENU_EDIT).performClick()
    assertThat(events).contains(MediaViewerEvent.EditClicked)

    composeTestRule.onNodeWithTag(MediaViewerScreenTestTags.OVERFLOW_BUTTON).performClick()
    composeTestRule.onNodeWithTag(MediaViewerScreenTestTags.MENU_SAVE).performClick()
    assertThat(events).contains(MediaViewerEvent.SaveClicked)

    composeTestRule.onNodeWithTag(MediaViewerScreenTestTags.OVERFLOW_BUTTON).performClick()
    composeTestRule.onNodeWithTag(MediaViewerScreenTestTags.MENU_DELETE).performClick()
    assertThat(events).contains(MediaViewerEvent.DeleteClicked)
  }

  @Test
  fun givenADraft_thenIExpectNoDeleteMenuItem() {
    setContent(createState(isDraft = true))

    composeTestRule.onNodeWithTag(MediaViewerScreenTestTags.OVERFLOW_BUTTON).performClick()

    composeTestRule.onNodeWithTag(MediaViewerScreenTestTags.MENU_SAVE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(MediaViewerScreenTestTags.MENU_DELETE).assertDoesNotExist()
  }

  @Test
  fun givenAnImage_whenITapShareAndForward_thenIExpectEvents() {
    setContent(createState())

    composeTestRule.onNodeWithTag(MediaViewerScreenTestTags.SHARE_BUTTON).performClick()
    assertThat(events).contains(MediaViewerEvent.ShareClicked)

    composeTestRule.onNodeWithTag(MediaViewerScreenTestTags.FORWARD_BUTTON).performClick()
    assertThat(events).contains(MediaViewerEvent.ForwardClicked)
  }

  @Test
  fun givenChromeHidden_thenIExpectNoToolbar() {
    setContent(createState(chromeVisible = false))

    composeTestRule.onNodeWithTag(MediaViewerScreenTestTags.TOP_BAR).assertDoesNotExist()
    composeTestRule.onNodeWithTag(MediaViewerScreenTestTags.SHARE_BUTTON).assertDoesNotExist()
  }

  @Test
  fun givenADeleteDialog_whenIConfirm_thenIExpectDeleteConfirmed() {
    setContent(createState(dialog = MediaViewerState.Dialog.DeleteConfirmation(canDeleteForEveryone = false, isNoteToSelf = false)))

    composeTestRule.onNodeWithTag(MediaViewerScreenTestTags.DELETE_BUTTON).performClick()

    assertThat(events).contains(MediaViewerEvent.DeleteConfirmed(deleteForEveryone = false))
  }

  @Test
  fun givenADeleteForEveryoneDialog_whenIConfirm_thenIExpectDeleteForEveryone() {
    setContent(createState(dialog = MediaViewerState.Dialog.DeleteConfirmation(canDeleteForEveryone = true, isNoteToSelf = false)))

    composeTestRule.onNodeWithTag(MediaViewerScreenTestTags.DELETE_FOR_EVERYONE_BUTTON).performClick()

    assertThat(events).contains(MediaViewerEvent.DeleteConfirmed(deleteForEveryone = true))
  }

  @Test
  fun givenADeleteDialogWithoutRemoteDelete_thenIExpectNoDeleteForEveryoneButton() {
    setContent(createState(dialog = MediaViewerState.Dialog.DeleteConfirmation(canDeleteForEveryone = false, isNoteToSelf = false)))

    composeTestRule.onNodeWithTag(MediaViewerScreenTestTags.DELETE_FOR_EVERYONE_BUTTON).assertDoesNotExist()
  }

  @Test
  fun givenAShortCaption_thenIExpectNoReadMore() {
    setContent(createState(items = listOf(createItem(1, caption = "short caption"))))

    composeTestRule.onNodeWithText("short caption", substring = true).assertIsDisplayed()
    composeTestRule.onNodeWithText("Read more", substring = true).assertDoesNotExist()
  }

  @Test
  fun givenALongCaption_whenITapReadMore_thenIExpectTheFullText() {
    // Robolectric measures glyphs at near-zero width, so overflow has to come from explicit
    // newlines rather than from a long single-line string.
    val longCaption = "caption line\n".repeat(8) + "THE END"
    setContent(createState(items = listOf(createItem(1, caption = longCaption))))

    composeTestRule.onNodeWithText("Read more", substring = true).assertIsDisplayed()
    composeTestRule.onNodeWithText("THE END", substring = true).assertDoesNotExist()

    composeTestRule.onNodeWithTag(MediaViewerScreenTestTags.CAPTION).performClick()

    composeTestRule.onNodeWithText("THE END", substring = true).assertIsDisplayed()
  }

  @Test
  fun givenALongCaptionWithShortLines_thenIExpectAPreviewBeforeReadMore() {
    val caption = (1..9).joinToString("\n")
    setContent(createState(items = listOf(createItem(1, caption = caption))))

    composeTestRule.onNodeWithTag(MediaViewerScreenTestTags.CAPTION)
      .assertIsDisplayed()
      .assertTextEquals("1\n2\n\u2026 Read more")
  }

  @Test
  fun givenASpoilerInTheCaption_whenITapIt_thenIExpectTheSpoilerRevealed() {
    // Robolectric lays glyphs out at near-zero width, so the tap lands just inside the caption padding, on the spoiler.
    val caption = StyledString("secret", styles = listOf(StyleRange(0, 6, StyleRange.Style.SPOILER)))
    // The spoiler particles animate every frame, so Compose never idles on its own.
    composeTestRule.mainClock.autoAdvance = false
    setContent(createState(messageBodies = mapOf(5L to caption)))
    composeTestRule.mainClock.advanceTimeByFrame()

    composeTestRule.onNodeWithText("secret", substring = true).assertDoesNotExist()

    composeTestRule.onNodeWithTag(MediaViewerScreenTestTags.CAPTION).performTouchInput { click(Offset(16.dp.toPx() + 0.5f, centerY)) }
    composeTestRule.mainClock.advanceTimeByFrame()

    composeTestRule.onNodeWithText("secret").assertExists()
  }

  @Test
  fun givenASingleItem_thenIExpectNoAlbumRail() {
    setContent(createState())

    composeTestRule.onNodeWithTag(MediaViewerScreenTestTags.ALBUM_RAIL).assertDoesNotExist()
  }

  @Test
  fun givenAnAlbum_whenITapAThumbnail_thenIExpectRailItemClicked() {
    val items = listOf(createItem(1), createItem(2), createItem(3))
    setContent(createState(items = items))

    composeTestRule.onNodeWithTag(MediaViewerScreenTestTags.ALBUM_RAIL).assertIsDisplayed()
    composeTestRule.onNodeWithTag(MediaViewerScreenTestTags.railItemTag(items[2])).performClick()

    assertThat(events).contains(MediaViewerEvent.RailItemClicked(AttachmentId(3)))
  }

  @Test
  fun givenAnImage_thenIExpectImageControlMode() {
    setContent(createState())

    composeTestRule.onNodeWithTag(MediaViewerScreenTestTags.SEEK_BAR).assertDoesNotExist()
    composeTestRule.onNodeWithTag(MediaViewerScreenTestTags.SHARE_BUTTON).assertIsDisplayed()
  }

  @Test
  fun givenAVideo_thenIExpectVideoControls() {
    setContent(
      createState(items = listOf(createItem(1, contentType = "video/mp4"))),
      playbackController = fixedStateController(PlaybackUiState(isAttached = true, isPlaying = true, positionMs = 7_000, durationMs = 45_000))
    )

    composeTestRule.onNodeWithTag(MediaViewerScreenTestTags.SEEK_BAR).assertIsDisplayed()
    composeTestRule.onNodeWithTag(MediaViewerScreenTestTags.PLAY_PAUSE_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(MediaViewerScreenTestTags.REWIND_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(MediaViewerScreenTestTags.FAST_FORWARD_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithText("0:07").assertIsDisplayed()
    composeTestRule.onNodeWithText("−0:38").assertIsDisplayed()
  }

  @Test
  fun givenAShortVideo_thenIExpectNoSkipButtons() {
    setContent(
      createState(items = listOf(createItem(1, contentType = "video/mp4"))),
      playbackController = fixedStateController(PlaybackUiState(isAttached = true, durationMs = 20_000))
    )

    composeTestRule.onNodeWithTag(MediaViewerScreenTestTags.REWIND_BUTTON).assertDoesNotExist()
    composeTestRule.onNodeWithTag(MediaViewerScreenTestTags.FAST_FORWARD_BUTTON).assertDoesNotExist()
  }

  @Test
  fun givenAPlaceholder_thenIExpectThePlaceholderPage() {
    setContent(createState(items = listOf(null)))

    composeTestRule.onNodeWithTag(MediaViewerScreenTestTags.PAGE_PLACEHOLDER).assertIsDisplayed()
  }

  /** A controller pinned to [state]: the video page's real attach/detach must not clobber the state under test. */
  private fun fixedStateController(state: PlaybackUiState): MediaViewerPlaybackController {
    return object : MediaViewerPlaybackController(state) {
      override fun attach(player: VideoPlayer, isLooping: Boolean) = Unit
      override fun detach(player: VideoPlayer) = Unit
    }
  }

  private fun setContent(
    state: MediaViewerState,
    playbackController: MediaViewerPlaybackController = MediaViewerPlaybackController()
  ) {
    composeTestRule.setContent {
      MediaViewerScreen(
        state = state,
        onEvent = { events += it },
        playbackController = playbackController
      )
    }
  }

  private fun createState(
    items: List<MediaViewerItem?> = listOf(createItem(1)),
    isDraft: Boolean = false,
    chromeVisible: Boolean = true,
    dialog: MediaViewerState.Dialog = MediaViewerState.Dialog.None,
    messageBodies: Map<Long, StyledString> = emptyMap()
  ): MediaViewerState {
    return MediaViewerState(
      loadState = MediaViewerState.LoadState.MEDIA_READY,
      items = items,
      position = 0,
      isDraft = isDraft,
      chromeVisible = chromeVisible,
      isInSharedAnimation = false,
      dialog = dialog,
      messageBodies = messageBodies
    )
  }

  private fun createItem(
    id: Long,
    contentType: String = "image/jpeg",
    caption: String? = null
  ): MediaViewerItem {
    return MediaViewerItem(
      attachmentId = AttachmentId(id),
      uri = Uri.parse("content://org.thoughtcrime.securesms/part/$id"),
      playbackUri = Uri.parse("content://org.thoughtcrime.securesms/part/$id"),
      contentType = contentType,
      size = 100L,
      isVideoGif = false,
      hasData = true,
      transferNeeded = false,
      messageId = 5L,
      threadId = 1L,
      date = 1_000L,
      formattedDate = "1 hour ago",
      formattedDateContentDescription = "one hour ago",
      isOutgoing = false,
      caption = caption,
      fromDisplayName = "Miles",
      threadDisplayName = "Spider Friends",
      isThreadGroup = true,
      isNoteToSelf = false
    )
  }
}
