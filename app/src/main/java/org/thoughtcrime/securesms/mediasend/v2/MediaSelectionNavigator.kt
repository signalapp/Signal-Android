package org.thoughtcrime.securesms.mediasend.v2

import androidx.navigation.NavController
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.navigation.safeNavigate

class MediaSelectionNavigator(
  private val toCamera: Int = -1,
  private val toGallery: Int = -1
) {
  fun goToReview(navController: NavController) {
    navController.popBackStack(R.id.mediaReviewFragment, false)
  }

  fun goToCamera(navController: NavController) {
    if (toCamera == -1) return

    navController.safeNavigate(toCamera)
  }

  fun goToGallery(navController: NavController) {
    if (toGallery == -1) return

    navController.safeNavigate(toGallery)
  }

  fun isPreviousScreenMediaReview(navController: NavController): Boolean {
    return navController.previousBackStackEntry?.destination?.id == R.id.mediaReviewFragment
  }
}
