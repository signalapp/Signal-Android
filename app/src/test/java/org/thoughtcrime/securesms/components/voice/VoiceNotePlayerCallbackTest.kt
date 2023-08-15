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
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.Mockito.anyBoolean
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class VoiceNotePlayerCallbackTest {

  private val context: Context = ApplicationProvider.getApplicationContext()
  private val mediaAudioAttributes = AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).setUsage(C.USAGE_MEDIA).build()
  private val callAudioAttributes = AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).setUsage(C.USAGE_VOICE_COMMUNICATION).build()
  private val session = mock(MediaSession::class.java)
  private val controllerInfo = mock(MediaSession.ControllerInfo::class.java)
  private val player: VoiceNotePlayer = mock(VoiceNotePlayer::class.java)
  private val testSubject = VoiceNotePlayerCallback(context, player)

  @Test
  fun `Given stream is media, When I onCommand for voice, then I expect the stream to switch to voice and continue playback`() {
    // GIVEN
    `when`(player.audioAttributes).thenReturn(mediaAudioAttributes)

    val command = SessionCommand(VoiceNotePlaybackService.ACTION_SET_AUDIO_STREAM, Bundle.EMPTY)
    val extras = Bundle().apply { putInt(VoiceNotePlaybackService.ACTION_SET_AUDIO_STREAM, AudioManager.STREAM_VOICE_CALL) }
    val expected = callAudioAttributes

    // WHEN
    testSubject.onCustomCommand(session, controllerInfo, command, extras)

    // THEN
    verify(player).playWhenReady = false
    verify(player).setAudioAttributes(expected, false)
    verify(player).playWhenReady = true
  }

  @Test
  fun `Given stream is voice, When I onCommand for media, then I expect the stream to switch to media and pause playback`() {
    // GIVEN
    `when`(player.audioAttributes).thenReturn(callAudioAttributes)

    val command = SessionCommand(VoiceNotePlaybackService.ACTION_SET_AUDIO_STREAM, Bundle.EMPTY)
    val extras = Bundle().apply { putInt(VoiceNotePlaybackService.ACTION_SET_AUDIO_STREAM, AudioManager.STREAM_MUSIC) }
    val expected = mediaAudioAttributes

    // WHEN
    testSubject.onCustomCommand(session, controllerInfo, command, extras)

    // THEN
    verify(player).playWhenReady = false
    verify(player).setAudioAttributes(expected, true)
    verify(player, Mockito.never()).playWhenReady = true
  }

  @Test
  fun `Given stream is voice, When I onCommand for voice, then I expect no change`() {
    // GIVEN
    `when`(player.audioAttributes).thenReturn(callAudioAttributes)

    val command = SessionCommand(VoiceNotePlaybackService.ACTION_SET_AUDIO_STREAM, Bundle.EMPTY)
    val extras = Bundle().apply { putInt(VoiceNotePlaybackService.ACTION_SET_AUDIO_STREAM, AudioManager.STREAM_VOICE_CALL) }

    // WHEN
    testSubject.onCustomCommand(session, controllerInfo, command, extras)

    // THEN
    verify(player, Mockito.never()).playWhenReady = anyBoolean()
    verify(player, Mockito.never()).setAudioAttributes(any(), eq(false))
  }

  @Test
  fun `Given stream is media, When I onCommand for media, then I expect no change`() {
    // GIVEN
    `when`(player.audioAttributes).thenReturn(mediaAudioAttributes)

    val command = SessionCommand(VoiceNotePlaybackService.ACTION_SET_AUDIO_STREAM, Bundle.EMPTY)
    val extras = Bundle().apply { putInt(VoiceNotePlaybackService.ACTION_SET_AUDIO_STREAM, AudioManager.STREAM_MUSIC) }

    // WHEN
    testSubject.onCustomCommand(session, controllerInfo, command, extras)

    // THEN
    verify(player, Mockito.never()).playWhenReady = anyBoolean()
    verify(player, Mockito.never()).setAudioAttributes(any(), anyBoolean())
  }
}
