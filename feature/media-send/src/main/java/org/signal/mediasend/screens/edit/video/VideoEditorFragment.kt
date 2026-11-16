/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit.video

import android.graphics.Outline
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import androidx.annotation.OptIn
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
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

  /** The range the player is clipped to, or null while it plays the whole video. */
  private var playerClip: LongRange? = null
  private lateinit var uri: Uri
  private lateinit var viewModel: VideoEditorViewModel
  private lateinit var player: VideoPlayer
  private lateinit var hud: VideoEditorPlayButtonLayout

  private val updatePosition = object : Runnable {
    override fun run() {
      if (IS_VIDEO_TRANSCODE_AVAILABLE && player.truePlaybackPosition >= 0) {
        emitPosition()
        handler.postDelayed(this, 100)
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

    shapeContentFrame(view)

    val contentFrame: View? = view.findViewById(R.id.exo_content_frame)
    contentFrame?.alpha = 0f
    val fadeInContent = {
      contentFrame?.animate()?.alpha(1f)?.setDuration(resources.getInteger(android.R.integer.config_shortAnimTime).toLong())
    }

    uri = requireArguments().getParcelableCompat(KEY_URI, Uri::class.java)!!
    isVideoGif = requireArguments().getBoolean(KEY_IS_VIDEO_GIF)
    maxSend = requireArguments().getLong(KEY_MAX_SEND)

    player.setWindow(requireActivity().window)
    player.setExoPlayerPool(MediaSendDependencies.exoPlayerPool)
    player.setVideoSource(uri, isVideoGif, TAG)

    if (!isVideoGif) {
      requireArguments().getParcelableCompat(KEY_TRIM_DATA, VideoTrimData::class.java)?.let { clipPlayer(it) }
    }

    hud.isVisible = !isVideoGif

    if (isVideoGif) {
      player.setPlayerCallback(object : VideoPlayer.PlayerCallback {
        override fun onPlaying() {
          viewModel.emitEvent(uri, VideoEditorViewModel.Event.PlayerReady)
        }

        override fun onFirstFrameRendered() {
          fadeInContent()
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

        override fun onFirstFrameRendered() {
          fadeInContent()
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
          val videoTrimData = getOrCreateVideoTrimData(uri)

          if (videoTrimData.isMuted) {
            player.mute()
          } else {
            player.unmute()
          }

          if (!isFocused) {
            bindVideoTimeline(videoTrimData)
          } else {
            hud.isVisible = isTouchEnabled && !isVideoGif
            onEditVideoDuration(videoTrimData, isTouchEnabled)
          }
        }
      } else {
        stopPositionUpdates()
        player.pause()

        if (!isVideoGif) {
          clipPlayer(getOrCreateVideoTrimData(uri))
          emitPosition()
        }
      }
    }
    isFocused = currentlyFocused
  }

  private fun bindVideoTimeline(data: VideoTrimData) {
    clipPlayer(data)
    hud.visibility = View.VISIBLE
    startPositionUpdates()
  }

  /** Re-clipping restarts the player, so it is skipped when the clip already matches. */
  private fun clipPlayer(data: VideoTrimData) {
    val clip = data.startTimeUs..data.endTimeUs
    if (!data.isDurationEdited || clip == playerClip) {
      return
    }

    player.clip(data.startTimeUs, data.endTimeUs, false)
    playerClip = clip
  }

  private fun emitPosition() {
    val playbackPosition = player.truePlaybackPosition
    if (playbackPosition >= 0) {
      viewModel.emitEvent(uri, VideoEditorViewModel.Event.ActualPositionChanged(playbackPosition.milliseconds.inWholeMicroseconds))
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
        playerClip = null
      }
      if (!wasPlayingBeforeEdit) {
        player.truePlaybackPosition = if (editingComplete) data.startTimeUs / 1000 else data.endTimeUs / 1000
      }
      if (editingComplete) {
        if (data.isDurationEdited) {
          player.clip(data.startTimeUs, data.endTimeUs, wasPlayingBeforeEdit)
          playerClip = data.startTimeUs..data.endTimeUs
        } else {
          player.removeClip(wasPlayingBeforeEdit)
          playerClip = null
        }

        if (!wasPlayingBeforeEdit) {
          hud.showPlayButton()
        }
      }
    }
  }

  /** The content frame tracks the video's rectangle, but fills everything it is given until the player reports a size. */
  @OptIn(UnstableApi::class)
  private fun shapeContentFrame(view: View) {
    val contentFrame: AspectRatioFrameLayout = view.findViewById(R.id.exo_content_frame) ?: return
    val radius = VIDEO_CORNER_RADIUS_DP * resources.displayMetrics.density

    contentFrame.outlineProvider = object : ViewOutlineProvider() {
      override fun getOutline(outlined: View, outline: Outline) {
        outline.setRoundRect(0, 0, outlined.width, outlined.height, radius)
      }
    }
    contentFrame.clipToOutline = true

    val width = requireArguments().getInt(KEY_WIDTH)
    val height = requireArguments().getInt(KEY_HEIGHT)
    if (width > 0 && height > 0) {
      contentFrame.setAspectRatio(width / height.toFloat())
    }
  }

  private fun onSeek(position: Long, dragComplete: Boolean) {
    if (dragComplete) {
      videoScanThrottle.clear()
    }

    videoScanThrottle.publish {
      player.pause()
      val milliseconds = position.microseconds.inWholeMilliseconds
      player.truePlaybackPosition = milliseconds
    }
  }

  companion object {
    private val TAG = Log.tag(VideoEditorFragment::class.java)

    private val IS_VIDEO_TRANSCODE_AVAILABLE = Build.VERSION.SDK_INT >= 26

    private const val VIDEO_CORNER_RADIUS_DP = 12f

    private const val KEY_URI = "uri"
    private const val KEY_MAX_SEND = "max_send_size"
    private const val KEY_IS_VIDEO_GIF = "is_video_gif"
    private const val KEY_WIDTH = "width"
    private const val KEY_HEIGHT = "height"
    private const val KEY_TRIM_DATA = "trim_data"

    fun arguments(uri: Uri, maxAttachmentSize: Long, isVideoGif: Boolean, width: Int = 0, height: Int = 0, trimData: VideoTrimData? = null): Bundle {
      return Bundle().apply {
        putParcelable(KEY_URI, uri)
        putLong(KEY_MAX_SEND, maxAttachmentSize)
        putBoolean(KEY_IS_VIDEO_GIF, isVideoGif)
        putInt(KEY_WIDTH, width)
        putInt(KEY_HEIGHT, height)
        putParcelable(KEY_TRIM_DATA, trimData)
      }
    }

    fun newInstance(uri: Uri, maxAttachmentSize: Long, isVideoGif: Boolean): VideoEditorFragment {
      return VideoEditorFragment().apply {
        arguments = arguments(uri, maxAttachmentSize, isVideoGif)
      }
    }
  }
}
