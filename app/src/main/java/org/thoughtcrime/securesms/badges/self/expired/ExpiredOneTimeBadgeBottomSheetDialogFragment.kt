package org.thoughtcrime.securesms.badges.self.expired

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import org.signal.core.models.badges.Badge
import org.signal.core.ui.BottomSheetUtil
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.ComposeBottomSheetDialogFragment
import org.signal.core.util.getParcelableCompat

/**
 * Fragment host for [ExpiredBadgeSheetEntry], for screens that show sheets as dialog fragments. Composable screens use
 * [ExpiredBadgeBottomSheet] instead.
 */
class ExpiredOneTimeBadgeBottomSheetDialogFragment : ComposeBottomSheetDialogFragment() {

  override val peekHeightPercentage: Float = 1f

  override val hasScrollingContent: Boolean = true

  @Composable
  override fun SheetContent() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      BottomSheets.Handle()
      ExpiredBadgeSheetEntry(
        badge = requireNotNull(requireArguments().getParcelableCompat(ARG_BADGE, Badge::class.java)),
        onDismiss = ::dismissAllowingStateLoss
      )
    }
  }

  companion object {
    private const val ARG_BADGE = "badge"

    @JvmStatic
    fun show(badge: Badge, fragmentManager: FragmentManager) {
      ExpiredOneTimeBadgeBottomSheetDialogFragment().apply {
        arguments = bundleOf(ARG_BADGE to badge)
        show(fragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
      }
    }
  }
}
