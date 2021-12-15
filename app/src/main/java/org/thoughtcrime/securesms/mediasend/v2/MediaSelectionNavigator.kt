package org.thoughtcrime.securesms.mediasend.v2

import android.Manifest
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.util.navigation.safeNavigate

class MediaSelectionNavigator(
  private val toCamera: Int = -1,
  private val toGallery: Int = -1
) {
  fun goToReview(view: View) {
    Navigation.findNavController(view).popBackStack(R.id.mediaReviewFragment, false)
  }

  fun goToCamera(view: View) {
    if (toCamera == -1) return

    Navigation.findNavController(view).safeNavigate(toCamera)
  }

  fun goToGallery(view: View) {
    if (toGallery == -1) return

    Navigation.findNavController(view).safeNavigate(toGallery)
  }

  companion object {
    fun Fragment.requestPermissionsForCamera(
      onGranted: () -> Unit
    ) {
      Permissions.with(this)
        .request(Manifest.permission.CAMERA)
        .ifNecessary()
        .withRationaleDialog(getString(R.string.ConversationActivity_to_capture_photos_and_video_allow_signal_access_to_the_camera), R.drawable.ic_camera_24)
        .withPermanentDenialDialog(getString(R.string.ConversationActivity_signal_needs_the_camera_permission_to_take_photos_or_video))
        .onAllGranted(onGranted)
        .onAnyDenied { Toast.makeText(requireContext(), R.string.ConversationActivity_signal_needs_camera_permissions_to_take_photos_or_video, Toast.LENGTH_LONG).show() }
        .execute()
    }

    fun Fragment.requestPermissionsForGallery(
      onGranted: () -> Unit
    ) {
      Permissions.with(this)
        .request(Manifest.permission.READ_EXTERNAL_STORAGE)
        .ifNecessary()
        .withPermanentDenialDialog(getString(R.string.AttachmentKeyboard_Signal_needs_permission_to_show_your_photos_and_videos))
        .onAllGranted(onGranted)
        .onAnyDenied { Toast.makeText(this.requireContext(), R.string.AttachmentKeyboard_Signal_needs_permission_to_show_your_photos_and_videos, Toast.LENGTH_LONG).show() }
        .execute()
    }
  }
}
