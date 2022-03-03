package org.thoughtcrime.securesms.mediapreview

import android.net.Uri
import io.reactivex.rxjava3.subjects.BehaviorSubject
import org.thoughtcrime.securesms.video.VideoPlayer

/**
 * Class to manage video playback in preview screen.
 */
class VideoControlsDelegate {

  private val playWhenReady: MutableMap<Uri, Boolean> = mutableMapOf()
  private val playerSubject = BehaviorSubject.create<Player>()

  fun getPlayerState(uri: Uri): PlayerState? {
    val player = playerSubject.value
    return if (player?.uri == uri && player.videoPlayer != null) {
      PlayerState(uri, player.videoPlayer.playbackPosition, player.videoPlayer.duration)
    } else {
      null
    }
  }

  fun pause() = playerSubject.value?.videoPlayer?.pause()

  fun resume(uri: Uri) {
    val player = playerSubject.value
    if (player?.uri == uri) {
      player.videoPlayer?.play()
    } else {
      playWhenReady[uri] = true
    }

    playerSubject.value?.videoPlayer?.play()
  }

  fun restart() {
    playerSubject.value?.videoPlayer?.playbackPosition = 0L
  }

  fun attachPlayer(uri: Uri, videoPlayer: VideoPlayer?) {
    playerSubject.onNext(Player(uri, videoPlayer))

    if (playWhenReady[uri] == true) {
      playWhenReady[uri] = false
      videoPlayer?.play()
    }
  }

  fun detachPlayer() {
    playerSubject.onNext(Player())
  }

  private data class Player(
    val uri: Uri = Uri.EMPTY,
    val videoPlayer: VideoPlayer? = null
  )

  data class PlayerState(
    val mediaUri: Uri,
    val position: Long,
    val duration: Long
  )
}
