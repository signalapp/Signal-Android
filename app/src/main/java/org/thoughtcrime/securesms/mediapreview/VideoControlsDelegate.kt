package org.thoughtcrime.securesms.mediapreview

import android.net.Uri
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.PublishSubject
import org.thoughtcrime.securesms.video.VideoPlayer

/**
 * Class to manage video playback in preview screen.
 */
class VideoControlsDelegate {

  private val playWhenReady: MutableMap<Uri, Boolean> = mutableMapOf()
  private val playerSubject = BehaviorSubject.create<Player>()
  private val playerReadySignal = PublishSubject.create<Unit>()
  val playerUpdates: Observable<PlayerUpdate> = playerReadySignal
    .observeOn(AndroidSchedulers.mainThread())
    .flatMap { playerSubject }
    .filter { it.videoPlayer != null }
    .map { PlayerUpdate(it.uri, it.videoPlayer?.duration!!) }

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

    if ((videoPlayer?.duration ?: -1L) > 0L) {
      playerReadySignal.onNext(Unit)
    } else {
      videoPlayer?.setPlayerStateCallbacks {
        playerReadySignal.onNext(Unit)
      }
    }

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

  data class PlayerUpdate(
    val mediaUri: Uri,
    val duration: Long
  )
}
