package org.thoughtcrime.securesms.badges.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.BadgeRepository
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.badges.models.LargeBadge
import org.thoughtcrime.securesms.components.FixedRoundedCornerBottomSheetDialogFragment
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.BottomSheetUtil
import org.thoughtcrime.securesms.util.MappingAdapter
import org.thoughtcrime.securesms.util.visible

class ViewBadgeBottomSheetDialogFragment : FixedRoundedCornerBottomSheetDialogFragment() {

  private val viewModel: ViewBadgeViewModel by viewModels(factoryProducer = { ViewBadgeViewModel.Factory(getStartBadge(), getRecipientId(), BadgeRepository(requireContext())) })

  override val peekHeightPercentage: Float = 1f

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    return inflater.inflate(R.layout.view_badge_bottom_sheet_dialog_fragment, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    postponeEnterTransition()

    val pager: ViewPager2 = view.findViewById(R.id.pager)
    val tabs: TabLayout = view.findViewById(R.id.tab_layout)
    val action: MaterialButton = view.findViewById(R.id.action)

    if (getRecipientId() == Recipient.self().id) {
      action.visible = false
    }

    val adapter = MappingAdapter()

    LargeBadge.register(adapter)
    pager.adapter = adapter
    adapter.submitList(listOf(LargeBadge.EmptyModel()))

    TabLayoutMediator(tabs, pager) { _, _ ->
    }.attach()

    pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
      override fun onPageSelected(position: Int) {
        if (adapter.getModel(position).map { it is LargeBadge.Model }.orElse(false)) {
          viewModel.onPageSelected(position)
        }
      }
    })

    viewModel.state.observe(viewLifecycleOwner) { state ->
      if (state.recipient == null || state.badgeLoadState == ViewBadgeState.LoadState.INIT) {
        return@observe
      }

      if (state.allBadgesVisibleOnProfile.isEmpty()) {
        dismissAllowingStateLoss()
      }

      tabs.visible = state.allBadgesVisibleOnProfile.size > 1

      adapter.submitList(
        state.allBadgesVisibleOnProfile.map {
          LargeBadge.Model(LargeBadge(it), state.recipient.getShortDisplayName(requireContext()))
        }
      ) {
        val stateSelectedIndex = state.allBadgesVisibleOnProfile.indexOf(state.selectedBadge)
        if (state.selectedBadge != null && pager.currentItem != stateSelectedIndex) {
          pager.currentItem = stateSelectedIndex
        }
      }
    }
  }

  private fun getStartBadge(): Badge? = requireArguments().getParcelable(ARG_START_BADGE)

  private fun getRecipientId(): RecipientId = requireNotNull(requireArguments().getParcelable(ARG_RECIPIENT_ID))

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
