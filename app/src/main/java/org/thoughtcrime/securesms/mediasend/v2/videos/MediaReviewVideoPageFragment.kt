package org.thoughtcrime.securesms.mediasend.v2.videos

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import org.signal.core.util.getParcelableCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.mediasend.VideoEditorFragment
import org.thoughtcrime.securesms.mediasend.v2.HudCommand
import org.thoughtcrime.securesms.mediasend.v2.MediaSelectionViewModel

private const val VIDEO_EDITOR_TAG = "video.editor.fragment"

/**
 * Page fragment which displays a single editable video (non-gif) to the user. Has an embedded MediaSendVideoFragment
 * and adds some extra support for saving and restoring state, as well as saving a video to disk.
 */
class MediaReviewVideoPageFragment : Fragment(R.layout.fragment_container), VideoEditorFragment.Controller {

  private val sharedViewModel: MediaSelectionViewModel by viewModels(ownerProducer = { requireActivity() })

  private lateinit var videoEditorFragment: VideoEditorFragment

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    videoEditorFragment = ensureVideoEditorFragment()
  }

  override fun onViewStateRestored(savedInstanceState: Bundle?) {
    super.onViewStateRestored(savedInstanceState)
    restoreVideoEditorState()
  }

  override fun onPlayerReady() {
    sharedViewModel.sendCommand(HudCommand.ResumeEntryTransition)
  }

  override fun onPlayerError() {
    sharedViewModel.sendCommand(HudCommand.ResumeEntryTransition)
  }

  override fun onTouchEventsNeeded(needed: Boolean) {
    sharedViewModel.setTouchEnabled(!needed)
  }
  private fun restoreVideoEditorState() {
    val data = sharedViewModel.getEditorState(requireUri()) as? VideoTrimData

    if (data != null) {
      videoEditorFragment.restoreState(data)
    }
  }

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
