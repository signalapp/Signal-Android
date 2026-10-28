package org.thoughtcrime.securesms.badges.view

import android.os.Bundle
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.fragment.app.FragmentManager
import org.signal.core.models.badges.Badge
import org.signal.core.models.recipients.RecipientId
import org.signal.core.ui.BottomSheetUtil
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.ComposeBottomSheetDialogFragment
import org.signal.core.util.getParcelableCompat

/**
 * Fragment host for [ViewBadgeSheetEntry], for screens that show sheets as dialog fragments. Composable screens use
 * [ViewBadgeBottomSheet] instead.
 */
class ViewBadgeBottomSheetDialogFragment : ComposeBottomSheetDialogFragment() {

  override val peekHeightPercentage: Float = 1f

  override val hasScrollingContent: Boolean = true

  @Composable
  override fun SheetContent() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      BottomSheets.Handle()
      ViewBadgeSheetEntry(
        recipientId = getRecipientId(),
        startBadge = getStartBadge(),
        onDismiss = ::dismissAllowingStateLoss
      )
    }
  }

  private fun getStartBadge(): Badge? = requireArguments().getParcelableCompat(ARG_START_BADGE, Badge::class.java)

  private fun getRecipientId(): RecipientId = requireNotNull(requireArguments().getParcelableCompat(ARG_RECIPIENT_ID, RecipientId::class.java))

  companion object {

    private const val ARG_START_BADGE = "start_badge"
    private const val ARG_RECIPIENT_ID = "recipient_id"

    @JvmStatic
    fun show(
      fragmentManager: FragmentManager,
      recipientId: RecipientId,
      startBadge: Badge? = null
    ) {
      ViewBadgeBottomSheetDialogFragment().apply {
        arguments = Bundle().apply {
          putParcelable(ARG_START_BADGE, startBadge)
          putParcelable(ARG_RECIPIENT_ID, recipientId)
        }

        show(fragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
      }
    }
  }
}
