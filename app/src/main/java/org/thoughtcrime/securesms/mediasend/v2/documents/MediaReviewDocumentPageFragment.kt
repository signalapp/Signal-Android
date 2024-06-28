package org.thoughtcrime.securesms.mediasend.v2.documents

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import org.signal.core.util.getParcelableCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.mediasend.Media
import org.thoughtcrime.securesms.mediasend.MediaSendDocumentFragment
import org.thoughtcrime.securesms.mediasend.v2.HudCommand
import org.thoughtcrime.securesms.mediasend.v2.MediaSelectionViewModel

private const val DOCUMENT_TAG = "media.send.document.fragment"

/**
 * Fragment which ensures we fire off ResumeEntryTransition when viewing a document.
 */
class MediaReviewDocumentPageFragment : Fragment(R.layout.fragment_container) {

  private lateinit var mediaSendDocumentFragment: MediaSendDocumentFragment

  private val sharedViewModel: MediaSelectionViewModel by viewModels(ownerProducer = { requireActivity() })

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    mediaSendDocumentFragment = ensureFragment()
    sharedViewModel.sendCommand(HudCommand.ResumeEntryTransition)
  }

  private fun ensureFragment(): MediaSendDocumentFragment {
    val fragmentInManager: MediaSendDocumentFragment? = childFragmentManager.findFragmentByTag(DOCUMENT_TAG) as? MediaSendDocumentFragment

    return if (fragmentInManager != null) {
      fragmentInManager
    } else {
      val mediaSendDocumentFragment = MediaSendDocumentFragment.newInstance(requireMedia())

      childFragmentManager.beginTransaction()
        .replace(
          R.id.fragment_container,
          mediaSendDocumentFragment,
          DOCUMENT_TAG
        )
        .commitAllowingStateLoss()

      mediaSendDocumentFragment
    }
  }

  private fun requireMedia(): Media = requireNotNull(requireArguments().getParcelableCompat(ARG_MEDIA, Media::class.java))

  companion object {
    private const val ARG_MEDIA = "arg.media"

    fun newInstance(media: Media): Fragment {
      return MediaReviewDocumentPageFragment().apply {
        arguments = Bundle().apply {
          putParcelable(ARG_MEDIA, media)
        }
      }
    }
  }
}
