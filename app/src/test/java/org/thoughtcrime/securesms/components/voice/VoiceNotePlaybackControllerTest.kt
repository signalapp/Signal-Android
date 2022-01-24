package org.thoughtcrime.securesms.components.voice

import android.app.Application
import android.media.AudioManager
import android.os.Bundle
import android.support.v4.media.session.MediaSessionCompat
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ControlDispatcher
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.audio.AudioAttributes
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.Mockito.any
import org.mockito.Mockito.anyBoolean
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class VoiceNotePlaybackControllerTest {

  private val mediaSessionCompat = mock(MediaSessionCompat::class.java)
  private val playbackParameters = VoiceNotePlaybackParameters(mediaSessionCompat)
  private val mediaAudioAttributes = AudioAttributes.Builder().setContentType(C.CONTENT_TYPE_MUSIC).setUsage(C.USAGE_MEDIA).build()
  private val callAudioAttributes = AudioAttributes.Builder().setContentType(C.CONTENT_TYPE_SPEECH).setUsage(C.USAGE_VOICE_COMMUNICATION).build()
  private val controlDispatcher = mock(ControlDispatcher::class.java)
  private val player: SimpleExoPlayer = mock(SimpleExoPlayer::class.java)
  private val testSubject = VoiceNotePlaybackController(player, playbackParameters)

  @Test
  fun `Given stream is media, When I onCommand for voice, then I expect the stream to switch to voice and continue playback`() {
    // GIVEN
    `when`(player.audioAttributes).thenReturn(mediaAudioAttributes)

    val command = VoiceNotePlaybackService.ACTION_SET_AUDIO_STREAM
    val extras = Bundle().apply { putInt(VoiceNotePlaybackService.ACTION_SET_AUDIO_STREAM, AudioManager.STREAM_VOICE_CALL) }
    val expected = callAudioAttributes

    // WHEN
    testSubject.onCommand(player, controlDispatcher, command, extras, null)

    // THEN
    verify(player).playWhenReady = false
    verify(player).setAudioAttributes(expected, false)
    verify(player).playWhenReady = true
  }

  @Test
  fun `Given stream is voice, When I onCommand for media, then I expect the stream to switch to media and pause playback`() {
    // GIVEN
    `when`(player.audioAttributes).thenReturn(callAudioAttributes)

    val command = VoiceNotePlaybackService.ACTION_SET_AUDIO_STREAM
    val extras = Bundle().apply { putInt(VoiceNotePlaybackService.ACTION_SET_AUDIO_STREAM, AudioManager.STREAM_MUSIC) }
    val expected = mediaAudioAttributes

    // WHEN
    testSubject.onCommand(player, controlDispatcher, command, extras, null)

    // THEN
    verify(player).playWhenReady = false
    verify(player).setAudioAttributes(expected, false)
    verify(player, Mockito.never()).playWhenReady = true
  }

  @Test
  fun `Given stream is voice, When I onCommand for voice, then I expect no change`() {
    // GIVEN
    `when`(player.audioAttributes).thenReturn(callAudioAttributes)

    val command = VoiceNotePlaybackService.ACTION_SET_AUDIO_STREAM
    val extras = Bundle().apply { putInt(VoiceNotePlaybackService.ACTION_SET_AUDIO_STREAM, AudioManager.STREAM_VOICE_CALL) }

    // WHEN
    testSubject.onCommand(player, controlDispatcher, command, extras, null)

    // THEN
    verify(player, Mockito.never()).playWhenReady = anyBoolean()
    verify(player, Mockito.never()).setAudioAttributes(any(), anyBoolean())
  }

  @Test
  fun `Given stream is media, When I onCommand for media, then I expect no change`() {
    // GIVEN
    `when`(player.audioAttributes).thenReturn(mediaAudioAttributes)

    val command = VoiceNotePlaybackService.ACTION_SET_AUDIO_STREAM
    val extras = Bundle().apply { putInt(VoiceNotePlaybackService.ACTION_SET_AUDIO_STREAM, AudioManager.STREAM_MUSIC) }

    // WHEN
    testSubject.onCommand(player, controlDispatcher, command, extras, null)

    // THEN
    verify(player, Mockito.never()).playWhenReady = anyBoolean()
    verify(player, Mockito.never()).setAudioAttributes(any(), anyBoolean())
  }
}
