package org.thoughtcrime.securesms.components.voice

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
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
    every { player.audioAttributes } returns mediaAudioAttributes

    val command = SessionCommand(VoiceNotePlaybackService.ACTION_SET_AUDIO_STREAM, Bundle.EMPTY)
    val extras = Bundle().apply { putInt(VoiceNotePlaybackService.ACTION_SET_AUDIO_STREAM, AudioManager.STREAM_MUSIC) }

    // WHEN
    testSubject.onCustomCommand(session, controllerInfo, command, extras)

    // THEN
    verify(exactly = 0) { player.playWhenReady = any() }
    verify(exactly = 0) { player.setAudioAttributes(any(), any()) }
  }
}
