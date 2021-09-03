package org.thoughtcrime.securesms.mediasend.v2.gallery

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.Navigation
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.mediasend.Media
import org.thoughtcrime.securesms.mediasend.v2.MediaSelectionNavigator
import org.thoughtcrime.securesms.mediasend.v2.MediaSelectionNavigator.Companion.requestPermissionsForCamera
import org.thoughtcrime.securesms.mediasend.v2.MediaSelectionViewModel
import org.thoughtcrime.securesms.permissions.Permissions

private const val MEDIA_GALLERY_TAG = "MEDIA_GALLERY"

class MediaSelectionGalleryFragment : Fragment(R.layout.fragment_container), MediaGalleryFragment.Callbacks {

  private lateinit var mediaGalleryFragment: MediaGalleryFragment

  private val navigator = MediaSelectionNavigator(
    toCamera = R.id.action_mediaGalleryFragment_to_mediaCaptureFragment
  )

  private val sharedViewModel: MediaSelectionViewModel by viewModels(
    ownerProducer = { requireActivity() }
  )

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    mediaGalleryFragment = ensureMediaGalleryFragment()

    sharedViewModel.state.observe(viewLifecycleOwner) { state ->
      mediaGalleryFragment.onViewStateUpdated(MediaGalleryFragment.ViewState(state.selectedMedia))
    }

    if (arguments?.containsKey("first") == true) {
      requireActivity().onBackPressedDispatcher.addCallback(
        viewLifecycleOwner,
        object : OnBackPressedCallback(true) {
          override fun handleOnBackPressed() {
            requireActivity().finish()
          }
        }
      )
    }
  }

  private fun ensureMediaGalleryFragment(): MediaGalleryFragment {
    val fragmentInManager: MediaGalleryFragment? = childFragmentManager.findFragmentByTag(MEDIA_GALLERY_TAG) as? MediaGalleryFragment

    return if (fragmentInManager != null) {
      fragmentInManager
    } else {
      val mediaGalleryFragment = MediaGalleryFragment()

      childFragmentManager.beginTransaction()
        .replace(
          R.id.fragment_container,
          mediaGalleryFragment,
          MEDIA_GALLERY_TAG
        )
        .commitNowAllowingStateLoss()

      mediaGalleryFragment
    }
  }

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults)
  }

  override fun isMultiselectEnabled(): Boolean {
    return true
  }

  override fun onMediaSelected(media: Media) {
    sharedViewModel.addMedia(media)
  }

  override fun onMediaUnselected(media: Media) {
    sharedViewModel.removeMedia(media)
  }

  override fun onSelectedMediaClicked(media: Media) {
    sharedViewModel.setFocusedMedia(media)
    navigator.goToReview(requireView())
  }

  override fun onNavigateToCamera() {
    requestPermissionsForCamera {
      navigator.goToCamera(requireView())
    }
  }

  override fun onSubmit() {
    navigator.goToReview(requireView())
  }

  override fun onToolbarNavigationClicked() {
    Navigation.findNavController(requireView()).popBackStack()
  }
}
