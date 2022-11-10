package org.thoughtcrime.securesms.components.voice

import android.media.AudioManager
import android.os.Bundle
import android.os.ResultReceiver
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.util.Util
import org.signal.core.util.logging.Log

class VoiceNotePlaybackController(
  private val player: ExoPlayer,
  private val voiceNotePlaybackParameters: VoiceNotePlaybackParameters
) : MediaSessionConnector.CommandReceiver {

  companion object {
    private val TAG = Log.tag(VoiceNoteMediaController::class.java)
  }

  override fun onCommand(p: Player, command: String, extras: Bundle?, cb: ResultReceiver?): Boolean {
    Log.d(TAG, "[onCommand] Received player command $command (extras? ${extras != null})")

    if (command == VoiceNotePlaybackService.ACTION_NEXT_PLAYBACK_SPEED) {
      val speed = extras?.getFloat(VoiceNotePlaybackService.ACTION_NEXT_PLAYBACK_SPEED, 1f) ?: 1f
      player.playbackParameters = PlaybackParameters(speed)
      voiceNotePlaybackParameters.setSpeed(speed)
      return true
    } else if (command == VoiceNotePlaybackService.ACTION_SET_AUDIO_STREAM) {
      val newStreamType: Int = extras?.getInt(VoiceNotePlaybackService.ACTION_SET_AUDIO_STREAM, AudioManager.STREAM_MUSIC) ?: AudioManager.STREAM_MUSIC

      val currentStreamType = Util.getStreamTypeForAudioUsage(player.audioAttributes.usage)
      if (newStreamType != currentStreamType) {
        val attributes = when (newStreamType) {
          AudioManager.STREAM_MUSIC -> AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).setUsage(C.USAGE_MEDIA).build()
          AudioManager.STREAM_VOICE_CALL -> AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).setUsage(C.USAGE_VOICE_COMMUNICATION).build()
          else -> throw AssertionError()
        }

        player.playWhenReady = false
        player.setAudioAttributes(attributes, newStreamType == AudioManager.STREAM_MUSIC)
        if (newStreamType == AudioManager.STREAM_VOICE_CALL) {
          player.playWhenReady = true
        }
      }
      return true
    }
    return false
  }
}
