/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.voice

import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.RequestMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.core.util.concurrent.addTo
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.DefaultValueLiveData
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil
import java.util.Optional

/**
 * This is a lifecycle-aware wrapper for the [MediaController].
 * Its main responsibilities are broadcasting playback state through [LiveData],
 * and resolving metadata values for a audio clip's URI into a [MediaItem] that media3 can understand.
 */
class VoiceNoteMediaController(val activity: FragmentActivity, private var postponeMediaControllerCreation: Boolean) : DefaultLifecycleObserver {

  val voiceNotePlaybackState = MutableLiveData(VoiceNotePlaybackState.NONE)
  val voiceNotePlayerViewState: LiveData<Optional<VoiceNotePlayerView.State>>
  private val disposables: LifecycleDisposable = LifecycleDisposable()
  private var mediaControllerProperty: MediaController? = null
  private lateinit var voiceNoteProximityWakeLockManager: VoiceNoteProximityWakeLockManager
  private var progressEventHandler: ProgressEventHandler? = null
  private var queuedPlayback: PlaybackItem? = null

  init {
    activity.lifecycle.addObserver(this)

    voiceNotePlayerViewState = voiceNotePlaybackState.switchMap { (uri, playheadPositionMillis, trackDuration, _, speed, isPlaying, clipType): VoiceNotePlaybackState ->
      if (clipType is VoiceNotePlaybackState.ClipType.Message) {
        val (messageId, senderId, threadRecipientId, messagePosition, threadId, timestamp) = clipType
        val sender = Recipient.live(senderId)
        val threadRecipient = Recipient.live(threadRecipientId)
        val name = LiveDataUtil.combineLatest(
          sender.liveDataResolved,
          threadRecipient.liveDataResolved
        ) { s: Recipient, t: Recipient -> VoiceNoteMediaItemFactory.getTitle(activity, s, t, null) }

        return@switchMap name.map<String, Optional<VoiceNotePlayerView.State>> { displayName: String ->
          Optional.of<VoiceNotePlayerView.State>(
            VoiceNotePlayerView.State(
              uri,
              messageId,
              threadId,
              !isPlaying,
              senderId,
              threadRecipientId,
              messagePosition,
              timestamp,
              displayName,
              playheadPositionMillis,
              trackDuration,
              speed
            )
          )
        }
      } else {
        return@switchMap DefaultValueLiveData<Optional<VoiceNotePlayerView.State>>(Optional.empty<VoiceNotePlayerView.State>())
      }
    }
  }

  override fun onStart(owner: LifecycleOwner) {
    super.onStart(owner)
    if (mediaControllerProperty == null && postponeMediaControllerCreation) {
      Log.i(TAG, "Postponing media controller creation. (${activity.localClassName}})")
      return
    }

    createMediaControllerAsync()
  }

  override fun onResume(owner: LifecycleOwner) {
    super.onResume(owner)

    progressEventHandler?.sendEmptyMessage(0)
  }

  override fun onPause(owner: LifecycleOwner) {
    clearProgressEventHandler()
    super.onPause(owner)
  }

  override fun onStop(owner: LifecycleOwner) {
    super.onStop(owner)
    mediaControllerProperty?.release()
    mediaControllerProperty = null
  }

  override fun onDestroy(owner: LifecycleOwner) {
    if (this::voiceNoteProximityWakeLockManager.isInitialized) {
      voiceNoteProximityWakeLockManager.unregisterCallbacksAndRelease()
      voiceNoteProximityWakeLockManager.unregisterFromLifecycle()
    }
    activity.lifecycle.removeObserver(this)
    super.onDestroy(owner)
  }

  fun finishPostpone() {
    if (mediaControllerProperty == null && postponeMediaControllerCreation && activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
      Log.i(TAG, "Finishing postponed media controller creation. (${activity.localClassName}})")
      createMediaControllerAsync()
    } else {
      Log.w(TAG, "Could not finish postponed media controller creation! (${activity.localClassName}})")
    }
  }

  private fun createMediaControllerAsync() {
    val applicationContext = activity.applicationContext
    val voiceNotePlaybackServiceSessionToken = SessionToken(applicationContext, ComponentName(applicationContext, VoiceNotePlaybackService::class.java))
    val mediaControllerBuilder = MediaController.Builder(applicationContext, voiceNotePlaybackServiceSessionToken)
    Observable.fromFuture(mediaControllerBuilder.buildAsync())
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe(
        { initializeMediaController(it) },
        { Log.w(TAG, "Could not initialize MediaController!", it) }
      )
      .addTo(disposables)
  }

  private fun initializeMediaController(uninitializedMediaController: MediaController) {
    postponeMediaControllerCreation = false

    voiceNoteProximityWakeLockManager = VoiceNoteProximityWakeLockManager(activity, uninitializedMediaController)
    uninitializedMediaController.addListener(PlaybackStateListener())
    Log.d(TAG, "MediaController successfully initialized. (${activity.localClassName})")
    mediaControllerProperty = uninitializedMediaController
    queuedPlayback?.let { startPlayback(it) }
    queuedPlayback = null
    notifyProgressEventHandler()
  }

  private fun notifyProgressEventHandler() {
    val mediaController = mediaControllerProperty
    if (mediaController == null) {
      Log.w(TAG, "Called notifyProgressEventHandler before controller was set. (${activity.localClassName})")
      return
    }
    if (progressEventHandler == null) {
      progressEventHandler = ProgressEventHandler(mediaController, voiceNotePlaybackState)
    }
    progressEventHandler?.sendEmptyMessage(0)
  }

  private fun clearProgressEventHandler() {
    progressEventHandler = null
  }

  fun startConsecutivePlayback(audioSlideUri: Uri, messageId: Long, progress: Double) {
    startPlayback(PlaybackItem(audioSlideUri, messageId, -1, progress, false))
  }

  fun startSinglePlayback(audioSlideUri: Uri, messageId: Long, progress: Double) {
    startPlayback(PlaybackItem(audioSlideUri, messageId, -1, progress, true))
  }

  fun startSinglePlaybackForDraft(draftUri: Uri, threadId: Long, progress: Double) {
    startPlayback(PlaybackItem(draftUri, -1, threadId, progress, true))
  }

  fun resumePlayback(audioSlideUri: Uri, messageId: Long) {
    val mediaController = mediaControllerProperty
    if (mediaController == null) {
      Log.w(TAG, "Tried to resume playback before the media controller was ready.")
      return
    }
    if (isCurrentTrack(audioSlideUri)) {
      mediaController.play()
    } else {
      startSinglePlayback(audioSlideUri, messageId, 0.0)
    }
  }

  fun pausePlayback(audioSlideUri: Uri) {
    if (isCurrentTrack(audioSlideUri)) {
      pausePlayback()
    } else {
      Log.i(TAG, "Tried to pause $audioSlideUri but currently playing item is ${getCurrentlyPlayingUri()}")
    }
  }

  fun pausePlayback() {
    val mediaController = mediaControllerProperty
    if (mediaController == null) {
      Log.w(TAG, "Tried to pause playback before the media controller was ready.")
      return
    }
    mediaController.pause()
  }

  fun seekToPosition(audioSlideUri: Uri, progress: Double) {
    val mediaController = mediaControllerProperty
    if (mediaController == null) {
      Log.w(TAG, "Tried to seekToPosition before the media controller was ready.")
      return
    }
    if (isCurrentTrack(audioSlideUri)) {
      mediaController.seekTo((mediaController.duration * progress).toLong())
    } else {
      Log.i(TAG, "Tried to seek $audioSlideUri but currently playing item is ${getCurrentlyPlayingUri()}")
    }
  }

  fun stopPlaybackAndReset(audioSlideUri: Uri) {
    val mediaController = mediaControllerProperty
    if (mediaController == null) {
      Log.w(TAG, "Tried to stopPlaybackAndReset before the media controller was ready.")
      return
    }
    if (isCurrentTrack(audioSlideUri)) {
      mediaController.stop()
    } else {
      Log.i(TAG, "Tried to stop $audioSlideUri but currently playing item is ${getCurrentlyPlayingUri()}")
    }
  }

  fun setPlaybackSpeed(audioSlideUri: Uri, playbackSpeed: Float) {
    val mediaController = mediaControllerProperty
    if (mediaController == null) {
      Log.w(TAG, "Tried to set playback speed before the media controller was ready.")
      return
    }

    if (isCurrentTrack(audioSlideUri)) {
      mediaController.setPlaybackSpeed(playbackSpeed)
    } else {
      Log.i(TAG, "Tried to set playback speed of $audioSlideUri but currently playing item is ${getCurrentlyPlayingUri()}")
    }
  }

  /**
   * Tells the Media service to begin playback of a given audio slide. If the audio
   * slide is currently playing, we jump to the desired position and then begin playback.
   *
   * @param audioSlideUri  The Uri of the desired audio slide
   * @param messageId      The Message id of the given audio slide
   * @param progress       The desired progress % to seek to.
   * @param singlePlayback The player will only play back the specified Uri, and not build a playlist.
   */
  private fun startPlayback(playbackItem: PlaybackItem) {
    val mediaController = mediaControllerProperty
    if (mediaController == null) {
      Log.w(TAG, "Tried to start playback before the media controller was ready.")
      queuedPlayback = playbackItem
      return
    }

    if (isCurrentTrack(playbackItem.audioSlideUri)) {
      val duration: Long = mediaController.duration
      mediaController.seekTo((duration * playbackItem.progress).toLong())
      mediaController.play()
    } else {
      val extras = bundleOf(
        EXTRA_MESSAGE_ID to playbackItem.messageId,
        EXTRA_THREAD_ID to playbackItem.threadId,
        EXTRA_PROGRESS to playbackItem.progress,
        EXTRA_PLAY_SINGLE to playbackItem.singlePlayback
      )
      val requestMetadata = RequestMetadata.Builder().setMediaUri(playbackItem.audioSlideUri).setExtras(extras).build()
      if (playbackItem.singlePlayback) {
        mediaController.clearMediaItems()
      }
      val mediaItem = MediaItem.Builder()
        .setUri(playbackItem.audioSlideUri)
        .setRequestMetadata(requestMetadata).build()
      mediaController.addMediaItem(mediaItem)
      mediaController.play()
    }
  }

  private fun isCurrentTrack(uri: Uri): Boolean {
    val mediaController = mediaControllerProperty
    if (mediaController == null) {
      Log.w(TAG, "Called isCurrentTrack before media controller was set. (${activity.localClassName}})")
      return false
    }
    return uri == getCurrentlyPlayingUri()
  }

  private fun isActivityResumed() = activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)

  private fun getCurrentlyPlayingUri(): Uri? = mediaControllerProperty?.currentMediaItem?.requestMetadata?.mediaUri

  inner class PlaybackStateListener : Player.Listener {
    override fun onEvents(player: Player, events: Player.Events) {
      super.onEvents(player, events)
      if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {
        if (!isActivityResumed()) {
          return
        }

        if (player.isPlaying) {
          notifyProgressEventHandler()
        } else {
          clearProgressEventHandler()
          if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
            voiceNotePlaybackState.postValue(VoiceNotePlaybackState.NONE)
          }
        }
      }
    }
  }

  private class ProgressEventHandler(
    private val mediaController: MediaController,
    private val voiceNotePlaybackState: MutableLiveData<VoiceNotePlaybackState>
  ) : Handler(Looper.getMainLooper()) {
    override fun handleMessage(msg: Message) {
      val newPlaybackState = constructPlaybackState(mediaController, voiceNotePlaybackState.value)
      voiceNotePlaybackState.postValue(newPlaybackState)
      val playerActive = mediaController.isPlaying
      if (playerActive) {
        sendEmptyMessageDelayed(0, 50)
      }
    }
  }

  companion object {
    private val TAG = Log.tag(VoiceNoteMediaController::class.java)

    var EXTRA_THREAD_ID = "voice.note.thread_id"
    var EXTRA_MESSAGE_ID = "voice.note.message_id"
    var EXTRA_PROGRESS = "voice.note.playhead"
    var EXTRA_PLAY_SINGLE = "voice.note.play.single"

    @JvmStatic
    private fun constructPlaybackState(
      mediaController: MediaController,
      previousState: VoiceNotePlaybackState?
    ): VoiceNotePlaybackState {
      val mediaUri = mediaController.currentMediaItem?.requestMetadata?.mediaUri
      return if (mediaController.isPlaying &&
        mediaUri != null
      ) {
        extractStateFromMetadata(mediaController, mediaUri, previousState)
      } else if (mediaController.playbackState == Player.STATE_READY && !mediaController.playWhenReady) {
        val position = mediaController.currentPosition
        val duration = mediaController.contentDuration
        if (previousState != null && position < duration) {
          previousState.asPaused()
        } else {
          VoiceNotePlaybackState.NONE
        }
      } else {
        VoiceNotePlaybackState.NONE
      }
    }

    @JvmStatic
    private fun extractStateFromMetadata(
      mediaController: MediaController,
      mediaUri: Uri,
      previousState: VoiceNotePlaybackState?
    ): VoiceNotePlaybackState {
      val speed = mediaController.playbackParameters.speed
      var duration = mediaController.contentDuration
      val mediaMetadata = mediaController.mediaMetadata
      var position = mediaController.currentPosition
      val autoReset = mediaUri == VoiceNoteMediaItemFactory.NEXT_URI || mediaUri == VoiceNoteMediaItemFactory.END_URI
      if (previousState != null && mediaUri == previousState.uri) {
        if (position < 0 && previousState.playheadPositionMillis >= 0) {
          position = previousState.playheadPositionMillis
        }
        if (duration <= 0 && previousState.trackDuration > 0) {
          duration = previousState.trackDuration
        }
      }
      return if (duration > 0 && position >= 0 && position <= duration) {
        VoiceNotePlaybackState(
          mediaUri,
          position,
          duration,
          autoReset,
          speed,
          mediaController.isPlaying,
          getClipType(mediaMetadata.extras)
        )
      } else {
        VoiceNotePlaybackState.NONE
      }
    }

    @JvmStatic
    private fun getClipType(mediaExtras: Bundle?): VoiceNotePlaybackState.ClipType {
      var messageId = -1L
      var senderId = RecipientId.UNKNOWN
      var messagePosition = -1L
      var threadId = -1L
      var threadRecipientId = RecipientId.UNKNOWN
      var timestamp = -1L
      if (mediaExtras != null) {
        messageId = mediaExtras.getLong(VoiceNoteMediaItemFactory.EXTRA_MESSAGE_ID, -1L)
        messagePosition = mediaExtras.getLong(VoiceNoteMediaItemFactory.EXTRA_MESSAGE_POSITION, -1L)
        threadId = mediaExtras.getLong(VoiceNoteMediaItemFactory.EXTRA_THREAD_ID, -1L)
        timestamp = mediaExtras.getLong(VoiceNoteMediaItemFactory.EXTRA_MESSAGE_TIMESTAMP, -1L)
        val serializedSenderId = mediaExtras.getString(VoiceNoteMediaItemFactory.EXTRA_INDIVIDUAL_RECIPIENT_ID)
        if (serializedSenderId != null) {
          senderId = RecipientId.from(serializedSenderId)
        }
        val serializedThreadRecipientId = mediaExtras.getString(VoiceNoteMediaItemFactory.EXTRA_THREAD_RECIPIENT_ID)
        if (serializedThreadRecipientId != null) {
          threadRecipientId = RecipientId.from(serializedThreadRecipientId)
        }
      }
      return if (messageId != -1L) {
        VoiceNotePlaybackState.ClipType.Message(
          messageId,
          senderId!!,
          threadRecipientId!!,
          messagePosition,
          threadId,
          timestamp
        )
      } else {
        VoiceNotePlaybackState.ClipType.Draft
      }
    }
  }

  /**
   * Holder class that contains everything one might need to begin voice note playback. Useful for queueing up items to play when the media controller is being initialized.
   */
  data class PlaybackItem(val audioSlideUri: Uri, val messageId: Long, val threadId: Long, val progress: Double, val singlePlayback: Boolean)
}
