package org.thoughtcrime.securesms.components.voice

import android.content.Context
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.ForwardingPlayer
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.audio.AudioAttributes
import org.thoughtcrime.securesms.video.exo.SignalMediaSourceFactory

class VoiceNotePlayer @JvmOverloads constructor(
  context: Context,
  val internalPlayer: SimpleExoPlayer = SimpleExoPlayer.Builder(context)
    .setMediaSourceFactory(SignalMediaSourceFactory(context))
    .setLoadControl(
      DefaultLoadControl.Builder()
        .setBufferDurationsMs(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE)
        .build()
    ).build().apply {
      setAudioAttributes(AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).setUsage(C.USAGE_MEDIA).build(), true)
    }
) : ForwardingPlayer(internalPlayer) {

  override fun seekTo(windowIndex: Int, positionMs: Long) {
    super.seekTo(windowIndex, positionMs)

    val isQueueToneIndex = windowIndex % 2 == 1
    val isSeekingToStart = positionMs == C.TIME_UNSET

    return if (isQueueToneIndex && isSeekingToStart) {
      val nextVoiceNoteWindowIndex = if (currentWindowIndex < windowIndex) windowIndex + 1 else windowIndex - 1
      if (mediaItemCount <= nextVoiceNoteWindowIndex) {
        super.seekTo(windowIndex, positionMs)
      } else {
        super.seekTo(nextVoiceNoteWindowIndex, positionMs)
      }
    } else {
      super.seekTo(windowIndex, positionMs)
    }
  }
}
