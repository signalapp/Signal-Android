package org.thoughtcrime.securesms.mediasend

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.mediasend.v2.MediaSelectionViewModel
import org.thoughtcrime.securesms.mediasend.v2.videos.VideoTrimData
import org.thoughtcrime.securesms.mms.MediaConstraints
import org.thoughtcrime.securesms.mms.VideoSlide
import org.thoughtcrime.securesms.scribbles.VideoEditorPlayButtonLayout
import org.thoughtcrime.securesms.util.Throttler
import org.thoughtcrime.securesms.util.visible
import org.thoughtcrime.securesms.video.VideoPlayer
import org.thoughtcrime.securesms.video.VideoPlayer.PlayerCallback
import org.thoughtcrime.securesms.video.videoconverter.VideoThumbnailsRangeSelectorView
import org.thoughtcrime.securesms.video.videoconverter.VideoThumbnailsRangeSelectorView.PositionDragListener
import java.io.IOException
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds

class VideoEditorFragment : Fragment(), PositionDragListener, MediaSendPageFragment {
  private val sharedViewModel: MediaSelectionViewModel by viewModels(ownerProducer = { requireActivity() })
  private val videoScanThrottle = Throttler(150)
  private val handler = Handler(Looper.getMainLooper())

  private var isVideoGif = false
  private var isInEdit = false
  private var isFocused = false
  private var wasPlayingBeforeEdit = false
  private var maxSend: Long = 0
  private lateinit var uri: Uri
  private lateinit var controller: Controller
  private lateinit var player: VideoPlayer
  private lateinit var hud: VideoEditorPlayButtonLayout
  private lateinit var videoTimeLine: VideoThumbnailsRangeSelectorView

  private val updatePosition = object : Runnable {
    override fun run() {
      if (MediaConstraints.isVideoTranscodeAvailable()) {
        val playbackPosition = player.truePlaybackPosition
        if (playbackPosition >= 0) {
          videoTimeLine.setActualPosition(playbackPosition.milliseconds.inWholeMicroseconds)
          handler.postDelayed(this, 100)
        }
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    controller = if (activity is Controller) {
      activity as Controller
    } else if (parentFragment is Controller) {
      parentFragment as Controller
    } else {
      throw IllegalStateException("Parent must implement Controller interface.")
    }
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    return inflater.inflate(R.layout.mediasend_video_fragment, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    videoTimeLine = requireActivity().findViewById(R.id.video_timeline)

    player = view.findViewById(R.id.video_player)
    hud = view.findViewById(R.id.video_editor_hud)

    uri = requireArguments().getParcelable(KEY_URI)!!
    isVideoGif = requireArguments().getBoolean(KEY_IS_VIDEO_GIF)
    maxSend = requireArguments().getLong(KEY_MAX_SEND)

    val slide = VideoSlide(requireContext(), uri, 0, isVideoGif)
    player.setWindow(requireActivity().window)
    player.setVideoSource(slide, isVideoGif, TAG)

    hud.visible = !slide.isVideoGif

    if (slide.isVideoGif) {
      player.setPlayerCallback(object : PlayerCallback {
        override fun onPlaying() {
          controller.onPlayerReady()
        }

        override fun onStopped() = Unit

        override fun onError() {
          controller.onPlayerError()
        }
      })
      player.hideControls()
      player.loopForever()
      player.play()
    } else {
      hud.setPlayClickListener {
        player.play()
      }
      player.setOnClickListener {
        player.pause()
        hud.showPlayButton()
      }

      player.setPlayerCallback(object : PlayerCallback {
        override fun onReady() {
          controller.onPlayerReady()
        }

        override fun onPlaying() {
          hud.fadePlayButton()
        }

        override fun onStopped() {
          hud.showPlayButton()
        }

        override fun onError() {
          controller.onPlayerError()
        }
      })
    }

    sharedViewModel.state.observe(viewLifecycleOwner) { incomingState ->
      val focusedUri = incomingState.focusedMedia?.uri
      val currentlyFocused = focusedUri != null && focusedUri == uri
      if (MediaConstraints.isVideoTranscodeAvailable()) {
        if (currentlyFocused) {
          if (isVideoGif) {
            player.play()
          } else {
            if (!isFocused) {
              bindVideoTimeline(incomingState.getOrCreateVideoTrimData(uri))
            } else {
              val videoTrimData = if (focusedUri != null) {
                incomingState.getOrCreateVideoTrimData(focusedUri)
              } else {
                VideoTrimData()
              }
              hud.visible = incomingState.isTouchEnabled && !isVideoGif
              onEditVideoDuration(videoTrimData, incomingState.isTouchEnabled)
            }
          }
        } else {
          stopPositionUpdates()
          player.pause()
        }
      }
      isFocused = currentlyFocused
    }
  }

  @RequiresApi(23)
  private fun bindVideoTimeline(data: VideoTrimData) {
    val autoplay = isVideoGif
    val slide = VideoSlide(requireContext(), uri, 0, autoplay)

    if (data.isDurationEdited) {
      player.clip(data.startTimeUs, data.endTimeUs, autoplay)
    }

    if (slide.hasVideo() && !autoplay) {
      try {
        videoTimeLine.registerPlayerDragListener(this)

        hud.visibility = View.VISIBLE
        startPositionUpdates()
      } catch (e: IOException) {
        Log.w(TAG, e)
      }
    }
  }

  override fun onPositionDrag(position: Long) {
    onSeek(position, false)
  }

  override fun onEndPositionDrag(position: Long) {
    onSeek(position, true)
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
    if (Build.VERSION.SDK_INT >= 23) {
      stopPositionUpdates()
      handler.post(updatePosition)
    }
  }

  private fun stopPositionUpdates() {
    handler.removeCallbacks(updatePosition)
  }

  override fun onHiddenChanged(hidden: Boolean) {
    if (hidden) {
      notifyHidden()
    }
  }

  override fun setUri(uri: Uri) {
    this.uri = uri
  }

  override fun getUri(): Uri {
    return uri
  }

  override fun saveState(): Any = Unit

  override fun restoreState(state: Any) = Unit

  override fun notifyHidden() {
    pausePlayback()
  }

  private fun pausePlayback() {
    player.pause()
    hud.showPlayButton()
  }

  @RequiresApi(23)
  private fun onEditVideoDuration(data: VideoTrimData, editingComplete: Boolean) {
    if (editingComplete) {
      isInEdit = false
      videoScanThrottle.clear()
    } else if (!isInEdit) {
      isInEdit = true
      wasPlayingBeforeEdit = player.isPlaying
    }

    if (wasPlayingBeforeEdit) {
      hud.hidePlayButton()
    }

    videoScanThrottle.publish {
      player.pause()
      if (!editingComplete) {
        player.removeClip(false)
      }
      player.playbackPosition = if (editingComplete) data.startTimeUs / 1000 else data.endTimeUs / 1000
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

  interface Controller {
    fun onPlayerReady()

    fun onPlayerError()

    fun onTouchEventsNeeded(needed: Boolean)
  }

  companion object {
    private val TAG = Log.tag(VideoEditorFragment::class.java)

    private const val KEY_URI = "uri"
    private const val KEY_MAX_SEND = "max_send_size"
    private const val KEY_IS_VIDEO_GIF = "is_video_gif"

    fun newInstance(uri: Uri, maxAttachmentSize: Long, isVideoGif: Boolean): VideoEditorFragment {
      val args = Bundle()
      args.putParcelable(KEY_URI, uri)
      args.putLong(KEY_MAX_SEND, maxAttachmentSize)
      args.putBoolean(KEY_IS_VIDEO_GIF, isVideoGif)

      val fragment = VideoEditorFragment()
      fragment.arguments = args
      fragment.setUri(uri)
      return fragment
    }
  }
}
