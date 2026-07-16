/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.edit.video

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.signal.core.util.Throttler
import org.signal.core.util.getParcelableCompat
import org.signal.core.util.logging.Log
import org.signal.mediasend.MediaSendDependencies
import org.signal.mediasend.R
import org.signal.video.VideoPlayer
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds

class VideoEditorFragment : Fragment() {
  private val videoScanThrottle = Throttler(150)
  private val handler = Handler(Looper.getMainLooper())

  private var isVideoGif = false
  private var isInEdit = false
  private var isFocused = false
  private var wasPlayingBeforeEdit = false
  private var maxSend: Long = 0
  private lateinit var uri: Uri
  private lateinit var viewModel: VideoEditorViewModel
  private lateinit var player: VideoPlayer
  private lateinit var hud: VideoEditorPlayButtonLayout

  private val updatePosition = object : Runnable {
    override fun run() {
      if (IS_VIDEO_TRANSCODE_AVAILABLE) {
        val playbackPosition = player.truePlaybackPosition
        if (playbackPosition >= 0) {
          viewModel.emitEvent(uri, VideoEditorViewModel.Event.ActualPositionChanged(playbackPosition.milliseconds.inWholeMicroseconds))
          handler.postDelayed(this, 100)
        }
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    viewModel = ViewModelProvider(requireActivity())[VideoEditorViewModel::class.java]
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    return inflater.inflate(R.layout.mediasend_video_fragment, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    player = view.findViewById(R.id.video_player)
    hud = view.findViewById(R.id.video_editor_hud)

    uri = requireArguments().getParcelableCompat(KEY_URI, Uri::class.java)!!
    isVideoGif = requireArguments().getBoolean(KEY_IS_VIDEO_GIF)
    maxSend = requireArguments().getLong(KEY_MAX_SEND)

    player.setWindow(requireActivity().window)
    player.setExoPlayerPool(MediaSendDependencies.exoPlayerPool)
    player.setVideoSource(uri, isVideoGif, TAG)

    hud.isVisible = !isVideoGif

    if (isVideoGif) {
      player.setPlayerCallback(object : VideoPlayer.PlayerCallback {
        override fun onPlaying() {
          viewModel.emitEvent(uri, VideoEditorViewModel.Event.PlayerReady)
        }

        override fun onStopped() = Unit

        override fun onError(e: Exception) {
          viewModel.emitEvent(uri, VideoEditorViewModel.Event.PlayerError)
        }
      })
      player.hideControls()
      player.loopForever()
      player.mute()
      player.disableAudioFocus()
      player.play()
    } else {
      hud.setPlayClickListener {
        player.play()
      }
      player.setOnClickListener {
        player.pause()
        hud.showPlayButton()
      }

      player.setPlayerCallback(object : VideoPlayer.PlayerCallback {
        override fun onReady() {
          viewModel.emitEvent(uri, VideoEditorViewModel.Event.PlayerReady)
        }

        override fun onPlaying() {
          hud.fadePlayButton()
        }

        override fun onStopped() {
          hud.showPlayButton()
        }

        override fun onError(e: Exception) {
          viewModel.emitEvent(uri, VideoEditorViewModel.Event.PlayerError)
        }
      })
    }

    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.commands(uri).collect { command ->
          when (command) {
            is VideoEditorViewModel.Command.PositionDrag -> onSeek(command.positionUs, dragComplete = false)
            is VideoEditorViewModel.Command.EndPositionDrag -> onSeek(command.positionUs, dragComplete = true)
          }
        }
      }
    }
  }

  fun onStateUpdate(focusedUri: Uri?, isTouchEnabled: Boolean, getOrCreateVideoTrimData: (Uri) -> VideoTrimData) {
    val currentlyFocused = focusedUri != null && focusedUri == uri
    if (IS_VIDEO_TRANSCODE_AVAILABLE) {
      if (currentlyFocused) {
        if (isVideoGif) {
          player.play()
        } else {
          if (!isFocused) {
            bindVideoTimeline(getOrCreateVideoTrimData(uri))
          } else {
            val videoTrimData = getOrCreateVideoTrimData(focusedUri)
            hud.isVisible = isTouchEnabled && !isVideoGif
            onEditVideoDuration(videoTrimData, isTouchEnabled)
          }
        }
      } else {
        stopPositionUpdates()
        player.pause()
      }
    }
    isFocused = currentlyFocused
  }

  private fun bindVideoTimeline(data: VideoTrimData) {
    val autoplay = isVideoGif

    if (data.isDurationEdited) {
      player.clip(data.startTimeUs, data.endTimeUs, autoplay)
    }

    if (!autoplay) {
      hud.visibility = View.VISIBLE
      startPositionUpdates()
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()

    player.cleanup()
  }

  override fun onPause() {
    super.onPause()
    notifyHidden()

    stopPositionUpdates()
  }

  override fun onResume() {
    super.onResume()
    startPositionUpdates()

    if (isVideoGif) {
      player.play()
    }
  }

  private fun startPositionUpdates() {
    stopPositionUpdates()
    handler.post(updatePosition)
  }

  private fun stopPositionUpdates() {
    handler.removeCallbacks(updatePosition)
  }

  override fun onHiddenChanged(hidden: Boolean) {
    if (hidden) {
      notifyHidden()
    }
  }

  private fun notifyHidden() {
    pausePlayback()
  }

  private fun pausePlayback() {
    player.pause()
    hud.showPlayButton()
  }

  private fun onEditVideoDuration(data: VideoTrimData, editingComplete: Boolean) {
    if (editingComplete) {
      isInEdit = false
      videoScanThrottle.clear()
    } else if (!isInEdit) {
      isInEdit = true
    }

    wasPlayingBeforeEdit = player.isPlaying

    if (wasPlayingBeforeEdit) {
      hud.hidePlayButton()
    }

    videoScanThrottle.publish {
      player.pause()
      if (!editingComplete) {
        player.removeClip(false)
      }
      if (!wasPlayingBeforeEdit) {
        player.playbackPosition = if (editingComplete) data.startTimeUs / 1000 else data.endTimeUs / 1000
      }
      if (editingComplete) {
        if (data.isDurationEdited) {
          player.clip(data.startTimeUs, data.endTimeUs, wasPlayingBeforeEdit)
        } else {
          player.removeClip(wasPlayingBeforeEdit)
        }

        if (!wasPlayingBeforeEdit) {
          hud.showPlayButton()
        }
      }
    }
  }

  private fun onSeek(position: Long, dragComplete: Boolean) {
    if (dragComplete) {
      videoScanThrottle.clear()
    }

    videoScanThrottle.publish {
      player.pause()
      val milliseconds = position.microseconds.inWholeMilliseconds
      player.playbackPosition = milliseconds
    }
  }

  companion object {
    private val TAG = Log.tag(VideoEditorFragment::class.java)

    private val IS_VIDEO_TRANSCODE_AVAILABLE = Build.VERSION.SDK_INT >= 26

    private const val KEY_URI = "uri"
    private const val KEY_MAX_SEND = "max_send_size"
    private const val KEY_IS_VIDEO_GIF = "is_video_gif"

    fun arguments(uri: Uri, maxAttachmentSize: Long, isVideoGif: Boolean): Bundle {
      return Bundle().apply {
        putParcelable(KEY_URI, uri)
        putLong(KEY_MAX_SEND, maxAttachmentSize)
        putBoolean(KEY_IS_VIDEO_GIF, isVideoGif)
      }
    }

    fun newInstance(uri: Uri, maxAttachmentSize: Long, isVideoGif: Boolean): VideoEditorFragment {
      return VideoEditorFragment().apply {
        arguments = arguments(uri, maxAttachmentSize, isVideoGif)
      }
    }
  }
}
