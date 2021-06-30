package org.thoughtcrime.securesms.components.voice

import android.os.Bundle
import android.os.ResultReceiver
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ext.mediasession.DefaultPlaybackController

class VoiceNotePlaybackController(private val voiceNotePlaybackParameters: VoiceNotePlaybackParameters) : DefaultPlaybackController() {

  override fun getCommands(): Array<String> {
    return arrayOf(VoiceNotePlaybackService.ACTION_NEXT_PLAYBACK_SPEED)
  }

  override fun onCommand(player: Player, command: String, extras: Bundle?, cb: ResultReceiver?) {
    if (command == VoiceNotePlaybackService.ACTION_NEXT_PLAYBACK_SPEED) {
      val speed = extras?.getFloat(VoiceNotePlaybackService.ACTION_NEXT_PLAYBACK_SPEED, 1f) ?: 1f

      player.playbackParameters = PlaybackParameters(speed)
      voiceNotePlaybackParameters.setSpeed(speed)
    }
  }
}
