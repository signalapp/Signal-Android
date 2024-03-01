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
import org.thoughtcrime.securesms.mediasend.v2.MediaSelectionState
import org.thoughtcrime.securesms.mediasend.v2.MediaSelectionViewModel
import org.thoughtcrime.securesms.mediasend.v2.videos.VideoTrimData
import org.thoughtcrime.securesms.mms.MediaConstraints
import org.thoughtcrime.securesms.mms.VideoSlide
import org.thoughtcrime.securesms.scribbles.VideoEditorPlayButtonLayout
import org.thoughtcrime.securesms.util.Throttler
import org.thoughtcrime.securesms.video.VideoPlayer
import org.thoughtcrime.securesms.video.VideoPlayer.PlayerCallback
import org.thoughtcrime.securesms.video.videoconverter.VideoThumbnailsRangeSelectorView
import org.thoughtcrime.securesms.video.videoconverter.VideoThumbnailsRangeSelectorView.OnRangeChangeListener
import org.thoughtcrime.securesms.video.videoconverter.VideoThumbnailsRangeSelectorView.Thumb
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.max

class VideoEditorFragment : Fragment(), OnRangeChangeListener, MediaSendPageFragment {
  private val sharedViewModel: MediaSelectionViewModel by viewModels(ownerProducer = { requireActivity() })
  private val videoScanThrottle = Throttler(150)
  private val handler = Handler(Looper.getMainLooper())

  private var data = VideoTrimData()
  private var canEdit = false
  private var isVideoGif = false
  private var isInEdit = false
  private var isFocused = false
  private var wasPlayingBeforeEdit = false
  private var maxVideoDurationUs: Long = 0
  private var maxSend: Long = 0
  private lateinit var uri: Uri
  private lateinit var controller: Controller
  private lateinit var player: VideoPlayer
  private lateinit var hud: VideoEditorPlayButtonLayout
  private lateinit var videoTimeLine: VideoThumbnailsRangeSelectorView

  private val updatePosition = object : Runnable {
    override fun run() {
      if (MediaConstraints.isVideoTranscodeAvailable()) {
        val playbackPositionUs = player.playbackPositionUs
        if (playbackPositionUs >= 0) {
          videoTimeLine.setActualPosition(playbackPositionUs)
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
    maxVideoDurationUs = TimeUnit.MILLISECONDS.toMicros(requireArguments().getLong(KEY_MAX_DURATION))

    val state = sharedViewModel.state.value!!
    val slide = VideoSlide(requireContext(), uri, 0, isVideoGif)
    player.setWindow(requireActivity().window)
    player.setVideoSource(slide, isVideoGif, TAG)

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
    } else if (MediaConstraints.isVideoTranscodeAvailable()) {
      hud.setPlayClickListener {
        player.play()
      }
      bindVideoTimeline(state)
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

    sharedViewModel.state.observe(viewLifecycleOwner) { state ->
      val focusedMedia = state.focusedMedia
      val currentlyFocused = focusedMedia?.uri != null && focusedMedia.uri == uri
      if (MediaConstraints.isVideoTranscodeAvailable() && canEdit && !isFocused && currentlyFocused) {
        bindVideoTimeline(state)
      }

      if (!currentlyFocused) {
        stopPositionUpdates()
      }
      isFocused = currentlyFocused
    }
  }

  @RequiresApi(23)
  private fun bindVideoTimeline(state: MediaSelectionState) {
    val uri = state.focusedMedia?.uri ?: return
    if (uri != this.uri) {
      return
    }

    val autoplay = isVideoGif
    val slide = VideoSlide(requireContext(), uri, 0, autoplay)

    if (data.isDurationEdited) {
      player.clip(data.startTimeUs, data.endTimeUs, autoplay)
    }
    if (slide.hasVideo()) {
      canEdit = true
      try {
        videoTimeLine.setOnRangeChangeListener(this)

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

  @RequiresApi(23)
  override fun onRangeDrag(minValueUs: Long, maxValueUs: Long, durationUs: Long, thumb: Thumb) {
    onEditVideoDuration(durationUs, minValueUs, maxValueUs, thumb == Thumb.MIN, false)
  }

  @RequiresApi(23)
  override fun onRangeDragEnd(minValueUs: Long, maxValueUs: Long, durationUs: Long, thumb: Thumb) {
    onEditVideoDuration(durationUs, minValueUs, maxValueUs, thumb == Thumb.MIN, true)
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

  override fun saveState(): Any {
    return data
  }

  override fun restoreState(state: Any) {
    if (state is VideoTrimData) {
      data = state
    } else {
      Log.w(TAG, "Received a bad saved state. Received class: " + state.javaClass.name)
    }
  }

  override fun notifyHidden() {
    pausePlayback()
  }

  private fun pausePlayback() {
    player.pause()
    hud.showPlayButton()
  }

  @RequiresApi(23)
  private fun onEditVideoDuration(totalDurationUs: Long, startTimeUs: Long, endTimeUs: Long, fromEdited: Boolean, editingComplete: Boolean) {
    controller.onTouchEventsNeeded(!editingComplete)

    hud.hidePlayButton()

    val clampedStartTime = max(startTimeUs.toDouble(), 0.0).toLong()

    val wasEdited = data.isDurationEdited
    val durationEdited = clampedStartTime > 0 || endTimeUs < totalDurationUs
    val endMoved = data.endTimeUs != endTimeUs

    val updatedData = MediaSelectionViewModel.clampToMaxClipDuration(VideoTrimData(durationEdited, totalDurationUs, clampedStartTime, endTimeUs), maxVideoDurationUs, !endMoved)

    if (editingComplete) {
      isInEdit = false
      videoScanThrottle.clear()
    } else if (!isInEdit) {
      isInEdit = true
      wasPlayingBeforeEdit = player.isPlaying
    }

    videoScanThrottle.publish {
      player.pause()
      if (!editingComplete) {
        player.removeClip(false)
      }
      player.playbackPosition = if (fromEdited || editingComplete) clampedStartTime / 1000 else endTimeUs / 1000
      if (editingComplete) {
        if (durationEdited) {
          player.clip(clampedStartTime, endTimeUs, wasPlayingBeforeEdit)
        } else {
          player.removeClip(wasPlayingBeforeEdit)
        }

        if (!wasPlayingBeforeEdit) {
          hud.showPlayButton()
        }
      }
    }

    if (!wasEdited && durationEdited) {
      controller.onVideoBeginEdit(uri)
    }

    if (editingComplete) {
      controller.onVideoEndEdit(uri)
    }

    uri.let {
      sharedViewModel.setEditorState(it, updatedData)
    }
  }

  private fun onSeek(position: Long, dragComplete: Boolean) {
    if (dragComplete) {
      videoScanThrottle.clear()
    }

    videoScanThrottle.publish {
      player.pause()
      player.playbackPosition = position
    }
  }

  interface Controller {
    fun onPlayerReady()

    fun onPlayerError()

    fun onTouchEventsNeeded(needed: Boolean)

    fun onVideoBeginEdit(uri: Uri)

    fun onVideoEndEdit(uri: Uri)
  }

  companion object {
    private val TAG = Log.tag(VideoEditorFragment::class.java)

    private const val KEY_URI = "uri"
    private const val KEY_MAX_SEND = "max_send_size"
    private const val KEY_IS_VIDEO_GIF = "is_video_gif"
    private const val KEY_MAX_DURATION = "max_duration"

    fun newInstance(uri: Uri, maxAttachmentSize: Long, isVideoGif: Boolean, maxVideoDuration: Long): VideoEditorFragment {
      val args = Bundle()
      args.putParcelable(KEY_URI, uri)
      args.putLong(KEY_MAX_SEND, maxAttachmentSize)
      args.putBoolean(KEY_IS_VIDEO_GIF, isVideoGif)
      args.putLong(KEY_MAX_DURATION, maxVideoDuration)

      val fragment = VideoEditorFragment()
      fragment.arguments = args
      fragment.setUri(uri)
      return fragment
    }
  }
}
