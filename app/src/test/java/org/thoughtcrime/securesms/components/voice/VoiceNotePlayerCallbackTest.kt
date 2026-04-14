package org.thoughtcrime.securesms.components.voice

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import assertk.assertions.isEqualTo
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class VoiceNotePlayerCallbackTest {
  private val context: Context = ApplicationProvider.getApplicationContext()
  private val mediaAudioAttributes = AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).setUsage(C.USAGE_MEDIA).build()
  private val callAudioAttributes = AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).setUsage(C.USAGE_VOICE_COMMUNICATION).build()
  private val session = mockk<MediaSession>()
  private val controllerInfo = mockk<MediaSession.ControllerInfo>()
  private val player = mockk<VoiceNotePlayer>(relaxUnitFun = true)
  private val testSubject = VoiceNotePlayerCallback(context, player)

  @Test
  fun `Given stream is media, When I onCommand for voice, then I expect the stream to switch to voice and continue playback`() {
    // GIVEN
    every { controllerInfo.packageName } returns context.packageName
    every { player.audioAttributes } returns mediaAudioAttributes

    val command = SessionCommand(VoiceNotePlaybackService.ACTION_SET_AUDIO_STREAM, Bundle.EMPTY)
    val extras = Bundle().apply { putInt(VoiceNotePlaybackService.ACTION_SET_AUDIO_STREAM, AudioManager.STREAM_VOICE_CALL) }
    val expected = callAudioAttributes

    // WHEN
    testSubject.onCustomCommand(session, controllerInfo, command, extras)

    // THEN
    verify { player.playWhenReady = false }
    verify { player.setAudioAttributes(expected, false) }
    verify { player.playWhenReady = true }
  }

  @Test
  fun `Given stream is voice, When I onCommand for media, then I expect the stream to switch to media and pause playback`() {
    // GIVEN
    every { controllerInfo.packageName } returns context.packageName
    every { player.audioAttributes } returns callAudioAttributes

    val command = SessionCommand(VoiceNotePlaybackService.ACTION_SET_AUDIO_STREAM, Bundle.EMPTY)
    val extras = Bundle().apply { putInt(VoiceNotePlaybackService.ACTION_SET_AUDIO_STREAM, AudioManager.STREAM_MUSIC) }
    val expected = mediaAudioAttributes

    // WHEN
    testSubject.onCustomCommand(session, controllerInfo, command, extras)

    // THEN
    verify { player.playWhenReady = false }
    verify { player.setAudioAttributes(expected, true) }
    verify(exactly = 0) { player.playWhenReady = true }
  }

  @Test
  fun `Given stream is voice, When I onCommand for voice, then I expect no change`() {
    // GIVEN
    every { controllerInfo.packageName } returns context.packageName
    every { player.audioAttributes } returns callAudioAttributes

    val command = SessionCommand(VoiceNotePlaybackService.ACTION_SET_AUDIO_STREAM, Bundle.EMPTY)
    val extras = Bundle().apply { putInt(VoiceNotePlaybackService.ACTION_SET_AUDIO_STREAM, AudioManager.STREAM_VOICE_CALL) }

    // WHEN
    testSubject.onCustomCommand(session, controllerInfo, command, extras)

    // THEN
    verify(exactly = 0) { player.playWhenReady = any() }
    verify(exactly = 0) { player.setAudioAttributes(any(), false) }
  }

  @Test
  fun `Given stream is media, When I onCommand for media, then I expect no change`() {
    // GIVEN
    every { controllerInfo.packageName } returns context.packageName
    every { player.audioAttributes } returns mediaAudioAttributes

    val command = SessionCommand(VoiceNotePlaybackService.ACTION_SET_AUDIO_STREAM, Bundle.EMPTY)
    val extras = Bundle().apply { putInt(VoiceNotePlaybackService.ACTION_SET_AUDIO_STREAM, AudioManager.STREAM_MUSIC) }

    // WHEN
    testSubject.onCustomCommand(session, controllerInfo, command, extras)

    // THEN
    verify(exactly = 0) { player.playWhenReady = any() }
    verify(exactly = 0) { player.setAudioAttributes(any(), any()) }
  }

  @Test
  fun `Given internal controller, When onConnect, then I expect full playback and custom commands`() {
    // GIVEN
    every { controllerInfo.packageName } returns context.packageName

    val customCommand = SessionCommand(VoiceNotePlaybackService.ACTION_SET_AUDIO_STREAM, Bundle.EMPTY)

    // WHEN
    val result = testSubject.onConnect(session, controllerInfo)

    // THEN
    assertThat(result.availablePlayerCommands.contains(androidx.media3.common.Player.COMMAND_SET_MEDIA_ITEM)).isTrue()
    assertThat(result.availableSessionCommands.contains(customCommand)).isTrue()
  }

  @Test
  fun `Given trusted external controller, When onConnect, then I expect playback controls without playlist mutation`() {
    // GIVEN
    every { controllerInfo.packageName } returns "com.android.systemui"
    every { controllerInfo.isTrusted } returns true

    val customCommand = SessionCommand(VoiceNotePlaybackService.ACTION_SET_AUDIO_STREAM, Bundle.EMPTY)

    // WHEN
    val result = testSubject.onConnect(session, controllerInfo)

    // THEN
    assertThat(result.availablePlayerCommands.contains(androidx.media3.common.Player.COMMAND_PLAY_PAUSE)).isTrue()
    assertThat(result.availablePlayerCommands.contains(androidx.media3.common.Player.COMMAND_SET_MEDIA_ITEM)).isFalse()
    assertThat(result.availablePlayerCommands.contains(androidx.media3.common.Player.COMMAND_CHANGE_MEDIA_ITEMS)).isFalse()
    assertThat(result.availableSessionCommands.contains(customCommand)).isFalse()
  }

  @Test
  fun `Given untrusted external controller, When onConnect, then I expect the connection to be rejected`() {
    // GIVEN
    every { controllerInfo.packageName } returns "com.bad.actor"
    every { controllerInfo.isTrusted } returns false

    // WHEN
    val result = testSubject.onConnect(session, controllerInfo)

    // THEN
    assertThat(result.availablePlayerCommands.contains(androidx.media3.common.Player.COMMAND_PLAY_PAUSE)).isFalse()
  }

  @Test
  fun `Given trusted external controller, When onCustomCommand, then I expect permission denied`() {
    // GIVEN
    every { controllerInfo.packageName } returns "com.android.systemui"
    every { controllerInfo.isTrusted } returns true
    every { player.audioAttributes } returns mediaAudioAttributes

    val command = SessionCommand(VoiceNotePlaybackService.ACTION_SET_AUDIO_STREAM, Bundle.EMPTY)
    val extras = Bundle().apply { putInt(VoiceNotePlaybackService.ACTION_SET_AUDIO_STREAM, AudioManager.STREAM_VOICE_CALL) }

    // WHEN
    val result = testSubject.onCustomCommand(session, controllerInfo, command, extras).get()

    // THEN
    assertThat(result.resultCode).isEqualTo(androidx.media3.session.SessionResult.RESULT_ERROR_PERMISSION_DENIED)
    verify(exactly = 0) { player.setAudioAttributes(any(), any()) }
  }

  @Test
  fun `Given trusted external controller, When onAddMediaItems, then I expect playlist mutation to be ignored`() {
    // GIVEN
    every { controllerInfo.packageName } returns "com.android.systemui"
    every { controllerInfo.isTrusted } returns true

    val mediaItem = MediaItem.Builder().setUri("content://org.signal.test/1").build()

    // WHEN
    val result = testSubject.onAddMediaItems(session, controllerInfo, mutableListOf(mediaItem)).get()

    // THEN
    assertThat(result.isEmpty()).isTrue()
    verify(exactly = 0) { player.clearMediaItems() }
  }
}
