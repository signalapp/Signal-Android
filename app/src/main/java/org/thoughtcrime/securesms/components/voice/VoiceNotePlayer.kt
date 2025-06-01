package org.thoughtcrime.securesms.components.voice

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import org.thoughtcrime.securesms.video.exo.SignalMediaSourceFactory

/**
 * A lightweight wrapper around ExoPlayer that compartmentalizes some logic and adds a few functions, most importantly the seek behavior.
 *
 * @param context
 */
@OptIn(UnstableApi::class)
class VoiceNotePlayer @JvmOverloads constructor(
  context: Context,
  internalPlayer: ExoPlayer = ExoPlayer.Builder(context)
    .setRenderersFactory(WorkaroundRenderersFactory(context))
    .setMediaSourceFactory(SignalMediaSourceFactory(context))
    .setLoadControl(
      DefaultLoadControl.Builder()
        .setBufferDurationsMs(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE)
        .build()
    )
    .setHandleAudioBecomingNoisy(true).build()
) : ForwardingPlayer(internalPlayer) {

  init {
    val audioManager = ContextCompat.getSystemService(context, AudioManager::class.java)

    val audioFocusRequest = if (Build.VERSION.SDK_INT >= 26) {
      AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(
          android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        )
        .setOnAudioFocusChangeListener {
        }
        .build()
    } else {
      null
    }

    addListener(object : Player.Listener {
      override fun onIsPlayingChanged(isPlaying: Boolean) {
        super.onIsPlayingChanged(isPlaying)
        if (Build.VERSION.SDK_INT >= 26) {
          if (isPlaying) {
            audioManager?.requestAudioFocus(audioFocusRequest!!)
          } else {
            audioManager?.abandonAudioFocusRequest(audioFocusRequest!!)
          }
        } else {
          if (isPlaying) {
            audioManager?.requestAudioFocus(
              null,
              AudioManager.STREAM_MUSIC,
              AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
          } else {
            audioManager?.abandonAudioFocus(null)
          }
        }
      }
    })
  }

  override fun seekTo(windowIndex: Int, positionMs: Long) {
    super.seekTo(windowIndex, positionMs)

    val isQueueToneIndex = windowIndex % 2 == 1
    val isSeekingToStart = positionMs == C.TIME_UNSET

    return if (isQueueToneIndex && isSeekingToStart) {
      val nextVoiceNoteWindowIndex = if (currentMediaItemIndex < windowIndex) windowIndex + 1 else windowIndex - 1
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

/**
 * @see RetryableInitAudioSink
 */
@OptIn(androidx.media3.common.util.UnstableApi::class)
class WorkaroundRenderersFactory(val context: Context) : DefaultRenderersFactory(context) {

  override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink {
    return RetryableInitAudioSink(context, enableFloatOutput, enableAudioTrackPlaybackParams)
  }
}
