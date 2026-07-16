package org.thoughtcrime.securesms.mediasend.v2.videos

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.signal.core.util.getParcelableCompat
import org.signal.mediasend.edit.video.VideoEditorFragment
import org.signal.mediasend.edit.video.VideoEditorViewModel
import org.signal.mediasend.edit.video.VideoThumbnailsRangeSelectorView
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.mediasend.v2.HudCommand
import org.thoughtcrime.securesms.mediasend.v2.MediaSelectionViewModel

private const val VIDEO_EDITOR_TAG = "video.editor.fragment"

/**
 * Page fragment which displays a single editable video (non-gif) to the user. Has an embedded MediaSendVideoFragment
 * and adds some extra support for saving and restoring state, as well as saving a video to disk.
 */
class MediaReviewVideoPageFragment : Fragment(R.layout.fragment_container) {

  private val sharedViewModel: MediaSelectionViewModel by viewModels(ownerProducer = { requireActivity() })
  private val videoEditorViewModel: VideoEditorViewModel by viewModels(ownerProducer = { requireActivity() })

  private lateinit var videoEditorFragment: VideoEditorFragment
  private lateinit var videoTimeLine: VideoThumbnailsRangeSelectorView

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    videoTimeLine = requireActivity().findViewById(R.id.video_timeline)

    videoEditorFragment = ensureVideoEditorFragment()

    videoTimeLine.registerPlayerDragListener(object : VideoThumbnailsRangeSelectorView.PositionDragListener {
      override fun onPositionDrag(position: Long) {
        focusedUri()?.let { videoEditorViewModel.sendCommand(it, VideoEditorViewModel.Command.PositionDrag(position)) }
      }

      override fun onEndPositionDrag(position: Long) {
        focusedUri()?.let { videoEditorViewModel.sendCommand(it, VideoEditorViewModel.Command.EndPositionDrag(position)) }
      }
    })

    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        videoEditorViewModel.events(requireUri()).collect { event ->
          when (event) {
            VideoEditorViewModel.Event.PlayerReady, VideoEditorViewModel.Event.PlayerError -> sharedViewModel.sendCommand(HudCommand.ResumeEntryTransition)
            is VideoEditorViewModel.Event.TouchEventsNeeded -> sharedViewModel.setTouchEnabled(!event.needed)
            is VideoEditorViewModel.Event.ActualPositionChanged -> videoTimeLine.setActualPosition(event.positionUs)
          }
        }
      }
    }

    sharedViewModel.state.observe(viewLifecycleOwner) { incomingState ->
      videoEditorFragment.onStateUpdate(
        incomingState.focusedMedia?.uri,
        incomingState.isTouchEnabled,
        incomingState::getOrCreateVideoTrimData
      )
    }
  }

  private fun focusedUri(): Uri? = sharedViewModel.state.value?.focusedMedia?.uri

  private fun ensureVideoEditorFragment(): VideoEditorFragment {
    val fragmentInManager: VideoEditorFragment? = childFragmentManager.findFragmentByTag(VIDEO_EDITOR_TAG) as? VideoEditorFragment

    return if (fragmentInManager != null) {
      fragmentInManager
    } else {
      val videoEditorFragment = VideoEditorFragment.newInstance(
        requireUri(),
        requireMaxAttachmentSize(),
        requireIsVideoGif()
      )

      childFragmentManager.beginTransaction()
        .replace(
          R.id.fragment_container,
          videoEditorFragment,
          VIDEO_EDITOR_TAG
        )
        .commitAllowingStateLoss()

      videoEditorFragment
    }
  }

  private fun requireUri(): Uri = requireNotNull(requireArguments().getParcelableCompat(ARG_URI, Uri::class.java))
  private fun requireMaxAttachmentSize(): Long = sharedViewModel.getMediaConstraints().getVideoMaxSize()
  private fun requireIsVideoGif(): Boolean = requireNotNull(requireArguments().getBoolean(ARG_IS_VIDEO_GIF))

  companion object {
    private const val ARG_URI = "arg.uri"
    private const val ARG_IS_VIDEO_GIF = "arg.is.video.gif"

    fun newInstance(uri: Uri, isVideoGif: Boolean): Fragment {
      return MediaReviewVideoPageFragment().apply {
        arguments = Bundle().apply {
          putParcelable(ARG_URI, uri)
          putBoolean(ARG_IS_VIDEO_GIF, isVideoGif)
        }
      }
    }
  }
}
