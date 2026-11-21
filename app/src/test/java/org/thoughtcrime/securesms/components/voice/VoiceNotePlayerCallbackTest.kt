package org.thoughtcrime.securesms.components.voice

import android.app.Application
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.AudioDeviceInfoBuilder

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class VoiceNotePlayerCallbackTest {
  private val context: Context = ApplicationProvider.getApplicationContext()
  private val audioManager = shadowOf(context.getSystemService(AudioManager::class.java))
  private val mediaAudioAttributes = AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).setUsage(C.USAGE_MEDIA).build()
  private val callAudioAttributes = AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).setUsage(C.USAGE_VOICE_COMMUNICATION).build()
  private val session = mockk<MediaSession>()
  private val controllerInfo = mockk<MediaSession.ControllerInfo>()
  private val player = mockk<ExoPlayer>(relaxUnitFun = true)
  private val testSubject = VoiceNotePlayerCallback(context, VoiceNotePlayer(context, player))

  @Test
  fun `raising the phone selects the built-in earpiece and continues playing`() {
    every { player.audioAttributes } returns mediaAudioAttributes
    every { player.playWhenReady } returns true
    val speaker = AudioDeviceInfoBuilder.newBuilder().setType(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER).build()
    val earpiece = AudioDeviceInfoBuilder.newBuilder().setType(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE).build()
    audioManager.setOutputDevices(listOf(speaker, earpiece))

    setAudioStream(AudioManager.STREAM_VOICE_CALL)

    verifyOrder {
      player.setPreferredAudioDevice(earpiece)
      player.setAudioAttributes(callAudioAttributes, false)
    }
    verify(exactly = 0) { player.pause() }
    verify(exactly = 0) { player.playWhenReady = any() }
  }

  @Test
  fun `raising the phone without an earpiece pauses playback`() {
    every { player.audioAttributes } returns mediaAudioAttributes
    every { player.playWhenReady } returns true
    audioManager.setOutputDevices(listOf(AudioDeviceInfoBuilder.newBuilder().setType(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER).build()))

    setAudioStream(AudioManager.STREAM_VOICE_CALL)

    verify { player.pause() }
    verify(exactly = 0) { player.setAudioAttributes(any(), any()) }
  }

  @Test
  fun `moving away pauses before releasing the earpiece`() {
    every { player.audioAttributes } returns callAudioAttributes

    setAudioStream(AudioManager.STREAM_MUSIC)

    verifyOrder {
      player.pause()
      player.setPreferredAudioDevice(null)
      player.setAudioAttributes(mediaAudioAttributes, false)
    }
    verify(exactly = 0) { player.playWhenReady = true }
  }

  @Test
  fun `a late near event after pause leaves the player alone`() {
    every { player.audioAttributes } returns mediaAudioAttributes
    every { player.playWhenReady } returns false

    setAudioStream(AudioManager.STREAM_VOICE_CALL)

    verify(exactly = 0) { player.setPreferredAudioDevice(any()) }
    verify(exactly = 0) { player.setAudioAttributes(any(), any()) }
    verify(exactly = 0) { player.playWhenReady = true }
  }

  @Test
  fun `repeated near and far events leave the current route and playback alone`() {
    every { player.audioAttributes } returns callAudioAttributes
    setAudioStream(AudioManager.STREAM_VOICE_CALL)

    every { player.audioAttributes } returns mediaAudioAttributes
    setAudioStream(AudioManager.STREAM_MUSIC)

    verify(exactly = 0) { player.pause() }
    verify(exactly = 0) { player.setPreferredAudioDevice(any()) }
    verify(exactly = 0) { player.setAudioAttributes(any(), any()) }
    verify(exactly = 0) { player.playWhenReady = any() }
  }

  private fun setAudioStream(streamType: Int) {
    val action = VoiceNotePlaybackService.ACTION_SET_AUDIO_STREAM
    val command = SessionCommand(action, Bundle.EMPTY)
    val extras = Bundle().apply { putInt(action, streamType) }
    testSubject.onCustomCommand(session, controllerInfo, command, extras)
  }
}
