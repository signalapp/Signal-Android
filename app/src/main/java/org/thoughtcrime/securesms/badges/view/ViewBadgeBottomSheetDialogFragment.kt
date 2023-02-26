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
import com.google.android.material.tabs.TabLayoutMediator
import org.signal.core.util.getParcelableCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.BadgeRepository
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.badges.models.LargeBadge
import org.thoughtcrime.securesms.components.FixedRoundedCornerBottomSheetDialogFragment
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppDonations
import org.thoughtcrime.securesms.databinding.ViewBadgeBottomSheetDialogFragmentBinding
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.BottomSheetUtil
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
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

  private val binding by ViewBinderDelegate(ViewBadgeBottomSheetDialogFragmentBinding::bind)

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    return inflater.inflate(R.layout.view_badge_bottom_sheet_dialog_fragment, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    postponeEnterTransition()

    if (getRecipientId() == Recipient.self().id) {
      binding.action.visible = false
    }

    @Suppress("CascadeIf")
    if (!InAppDonations.hasAtLeastOnePaymentMethodAvailable()) {
      binding.noSupport.visible = true
      binding.action.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_open_20)
      binding.action.setText(R.string.preferences__donate_to_signal)
      binding.action.setOnClickListener {
        CommunicationActions.openBrowserLink(requireContext(), getString(R.string.donate_url))
      }
    } else if (Recipient.self().badges.none { it.category == Badge.Category.Donor && !it.isBoost() && !it.isExpired() }) {
      binding.action.setOnClickListener {
        startActivity(AppSettingsActivity.subscriptions(requireContext()))
      }
    } else {
      binding.action.visible = false
    }

    val adapter = MappingAdapter()

    LargeBadge.register(adapter)
    binding.pager.adapter = adapter
    adapter.submitList(listOf(LargeBadge.EmptyModel()))

    TabLayoutMediator(binding.tabLayout, binding.pager) { _, _ ->
    }.attach()

    binding.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
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

      binding.tabLayout.visible = state.allBadgesVisibleOnProfile.size > 1
      binding.singlePageSpace.visible = state.allBadgesVisibleOnProfile.size > 1

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
        if (state.selectedBadge != null && binding.pager.currentItem != stateSelectedIndex) {
          binding.pager.currentItem = stateSelectedIndex
        }
      }
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
