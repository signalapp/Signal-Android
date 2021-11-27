package org.thoughtcrime.securesms.badges.view

import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
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
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.BottomSheetUtil
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.FeatureFlags
import org.thoughtcrime.securesms.util.MappingAdapter
import org.thoughtcrime.securesms.util.PlayServicesUtil
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.visible
import kotlin.math.ceil
import kotlin.math.max

class ViewBadgeBottomSheetDialogFragment : FixedRoundedCornerBottomSheetDialogFragment() {

  private val viewModel: ViewBadgeViewModel by viewModels(factoryProducer = { ViewBadgeViewModel.Factory(getStartBadge(), getRecipientId(), BadgeRepository(requireContext())) })

  override val peekHeightPercentage: Float = 1f

  private val textWidth: Float
    get() = (resources.displayMetrics.widthPixels - ViewUtil.dpToPx(64)).toFloat()
  private val textBounds: Rect = Rect()
  private val textPaint: Paint = Paint().apply {
    textSize = ViewUtil.spToPx(16f).toFloat()
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    return inflater.inflate(R.layout.view_badge_bottom_sheet_dialog_fragment, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    postponeEnterTransition()

    val pager: ViewPager2 = view.findViewById(R.id.pager)
    val tabs: TabLayout = view.findViewById(R.id.tab_layout)
    val action: MaterialButton = view.findViewById(R.id.action)
    val noSupport: View = view.findViewById(R.id.no_support)

    if (getRecipientId() == Recipient.self().id) {
      action.visible = false
    }

    @Suppress("CascadeIf")
    if (PlayServicesUtil.getPlayServicesStatus(requireContext()) != PlayServicesUtil.PlayServicesStatus.SUCCESS) {
      noSupport.visible = true
      action.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_open_20)
      action.setText(R.string.preferences__donate_to_signal)
      action.setOnClickListener {
        CommunicationActions.openBrowserLink(requireContext(), getString(R.string.donate_url))
      }
    } else if (
      FeatureFlags.donorBadges() &&
      Recipient.self().badges.none { it.category == Badge.Category.Donor && !it.isBoost() && !it.isExpired() }
    ) {
      action.setOnClickListener {
        startActivity(AppSettingsActivity.subscriptions(requireContext()))
      }
    } else {
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

      var maxLines = 3
      state.allBadgesVisibleOnProfile.forEach { badge ->
        val text = badge.resolveDescription(state.recipient.getShortDisplayName(requireContext()))
        textPaint.getTextBounds(text, 0, text.length, textBounds)
        val estimatedLines = ceil(textBounds.width().toFloat() / textWidth).toInt()
        maxLines = max(maxLines, estimatedLines)
      }

      adapter.submitList(
        state.allBadgesVisibleOnProfile.map {
          LargeBadge.Model(LargeBadge(it), state.recipient.getShortDisplayName(requireContext()), maxLines + 1)
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
      if (!FeatureFlags.displayDonorBadges() && recipientId != Recipient.self().id) {
        return
      }

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
