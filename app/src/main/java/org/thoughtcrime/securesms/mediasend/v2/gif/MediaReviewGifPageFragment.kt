package org.thoughtcrime.securesms.mediasend.v2.gif

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import org.signal.core.util.getParcelableCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.mediasend.MediaSendGifFragment
import org.thoughtcrime.securesms.mediasend.v2.HudCommand
import org.thoughtcrime.securesms.mediasend.v2.MediaSelectionViewModel

private const val GIF_TAG = "media.send.gif.fragment"

/**
 * Fragment which ensures we fire off ResumeEntryTransition when viewing a non-video gif.
 */
class MediaReviewGifPageFragment : Fragment(R.layout.fragment_container) {

  private lateinit var mediaSendGifFragment: MediaSendGifFragment

  private val sharedViewModel: MediaSelectionViewModel by viewModels(ownerProducer = { requireActivity() })

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    mediaSendGifFragment = ensureGifFragment()
    sharedViewModel.sendCommand(HudCommand.ResumeEntryTransition)
  }

  private fun ensureGifFragment(): MediaSendGifFragment {
    val fragmentInManager: MediaSendGifFragment? = childFragmentManager.findFragmentByTag(GIF_TAG) as? MediaSendGifFragment

    return if (fragmentInManager != null) {
      sharedViewModel.sendCommand(HudCommand.ResumeEntryTransition)
      fragmentInManager
    } else {
      val mediaSendGifFragment = MediaSendGifFragment.newInstance(
        requireUri()
      )

      childFragmentManager.beginTransaction()
        .replace(
          R.id.fragment_container,
          mediaSendGifFragment,
          GIF_TAG
        )
        .commitAllowingStateLoss()

      mediaSendGifFragment
    }
  }

  private fun requireUri(): Uri = requireNotNull(requireArguments().getParcelableCompat(ARG_URI, Uri::class.java))

  companion object {
    private const val ARG_URI = "arg.uri"

    fun newInstance(uri: Uri): Fragment {
      return MediaReviewGifPageFragment().apply {
        arguments = Bundle().apply {
          putParcelable(ARG_URI, uri)
        }
      }
    }
  }
}
