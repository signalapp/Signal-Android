package org.thoughtcrime.securesms.mediapreview

import android.net.Uri
import org.thoughtcrime.securesms.video.VideoPlayer

/**
 * Class to manage video playback in preview screen.
 */
class VideoControlsDelegate {

  private val playWhenReady: MutableMap<Uri, Boolean> = mutableMapOf()
  private var player: Player? = null
  private var isMuted: Boolean = true

  fun getPlayerState(uri: Uri): PlayerState? {
    val player: Player? = this.player
    return if (player?.uri == uri && player.videoPlayer != null) {
      PlayerState(uri, player.videoPlayer.playbackPosition, player.videoPlayer.duration, player.isGif, player.loopCount)
    } else {
      null
    }
  }

  fun pause() = player?.videoPlayer?.pause()

  fun resume(uri: Uri) {
    if (player?.uri == uri) {
      player?.videoPlayer?.play()
    } else {
      playWhenReady[uri] = true
    }
  }

  fun restart() {
    player?.videoPlayer?.playbackPosition = 0L
  }

  fun onPlayerPositionDiscontinuity(reason: Int) {
    val player = this.player
    if (player != null && player.isGif) {
      this.player = player.copy(loopCount = if (reason == 0) player.loopCount + 1 else 0)
    }
  }

  fun mute() {
    isMuted = true
    player?.videoPlayer?.mute()
  }

  fun unmute() {
    isMuted = false
    player?.videoPlayer?.unmute()
  }

  fun hasAudioStream(): Boolean {
    return player?.videoPlayer?.hasAudioTrack() ?: false
  }

  fun attachPlayer(uri: Uri, videoPlayer: VideoPlayer?, isGif: Boolean) {
    player = Player(uri, videoPlayer, isGif)

    if (isMuted) {
      videoPlayer?.mute()
    } else {
      videoPlayer?.unmute()
    }

    if (playWhenReady[uri] == true) {
      playWhenReady[uri] = false
      videoPlayer?.play()
    }
  }

  fun detachPlayer() {
    player = Player()
  }

  private data class Player(
    val uri: Uri = Uri.EMPTY,
    val videoPlayer: VideoPlayer? = null,
    val isGif: Boolean = false,
    val loopCount: Int = 0
  )

  data class PlayerState(
    val mediaUri: Uri,
    val position: Long,
    val duration: Long,
    val isGif: Boolean,
    val loopCount: Int
  )
}
