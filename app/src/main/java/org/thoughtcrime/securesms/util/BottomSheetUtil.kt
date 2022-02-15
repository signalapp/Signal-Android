package org.thoughtcrime.securesms.util

import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.thoughtcrime.securesms.R

object BottomSheetUtil {
  const val STANDARD_BOTTOM_SHEET_FRAGMENT_TAG = "BOTTOM"

  /**
   * Show preventing a possible IllegalStateException.
   */
  @JvmStatic
  fun show(
    manager: FragmentManager,
    tag: String?,
    dialog: BottomSheetDialogFragment
  ) {
    manager.beginTransaction().apply {
      add(dialog, tag)
      commitAllowingStateLoss()
    }
  }

  fun BottomSheetDialogFragment.requireCoordinatorLayout(): CoordinatorLayout {
    return requireDialog().findViewById(R.id.coordinator)
  }
}
