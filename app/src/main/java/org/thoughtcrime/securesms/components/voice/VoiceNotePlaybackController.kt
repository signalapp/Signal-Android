package org.thoughtcrime.securesms.components.voice

import android.media.AudioManager
import android.os.Bundle
import android.os.ResultReceiver
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.ext.mediasession.DefaultPlaybackController
import com.google.android.exoplayer2.util.Util

class VoiceNotePlaybackController(private val voiceNotePlaybackParameters: VoiceNotePlaybackParameters) : DefaultPlaybackController() {

  override fun getCommands(): Array<String> {
    return arrayOf(VoiceNotePlaybackService.ACTION_NEXT_PLAYBACK_SPEED, VoiceNotePlaybackService.ACTION_SET_AUDIO_STREAM)
  }

  override fun onCommand(player: Player, command: String, extras: Bundle?, cb: ResultReceiver?) {
    if (command == VoiceNotePlaybackService.ACTION_NEXT_PLAYBACK_SPEED) {
      val speed = extras?.getFloat(VoiceNotePlaybackService.ACTION_NEXT_PLAYBACK_SPEED, 1f) ?: 1f

      player.playbackParameters = PlaybackParameters(speed)
      voiceNotePlaybackParameters.setSpeed(speed)
    } else if (command == VoiceNotePlaybackService.ACTION_SET_AUDIO_STREAM) {
      val newStreamType: Int = extras?.getInt(VoiceNotePlaybackService.ACTION_SET_AUDIO_STREAM, AudioManager.STREAM_MUSIC) ?: AudioManager.STREAM_MUSIC

      val currentStreamType = Util.getStreamTypeForAudioUsage((player as SimpleExoPlayer).audioAttributes.usage)
      if (newStreamType != currentStreamType) {
        val attributes = when (newStreamType) {
          AudioManager.STREAM_MUSIC -> AudioAttributes.Builder().setContentType(C.CONTENT_TYPE_MUSIC).setUsage(C.USAGE_MEDIA).build()
          AudioManager.STREAM_VOICE_CALL -> AudioAttributes.Builder().setContentType(C.CONTENT_TYPE_SPEECH).setUsage(C.USAGE_VOICE_COMMUNICATION).build()
          else -> throw AssertionError()
        }

        player.playWhenReady = false
        player.audioAttributes = attributes

        if (newStreamType == AudioManager.STREAM_VOICE_CALL) {
          player.playWhenReady = true
        }
      }
    }
  }
}
