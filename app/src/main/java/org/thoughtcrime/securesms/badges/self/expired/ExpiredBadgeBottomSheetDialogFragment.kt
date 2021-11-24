package org.thoughtcrime.securesms.badges.self.expired

import androidx.fragment.app.FragmentManager
import org.signal.core.util.DimensionUnit
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.badges.models.ExpiredBadge
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsAdapter
import org.thoughtcrime.securesms.components.settings.DSLSettingsBottomSheetFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.BottomSheetUtil

/**
 * Bottom sheet displaying a fading badge with a notice and action for becoming a subscriber again.
 */
class ExpiredBadgeBottomSheetDialogFragment : DSLSettingsBottomSheetFragment(
  peekHeightPercentage = 1f
) {
  override fun bindAdapter(adapter: DSLSettingsAdapter) {
    ExpiredBadge.register(adapter)

    adapter.submitList(getConfiguration().toMappingModelList())
  }

  private fun getConfiguration(): DSLConfiguration {
    val badge: Badge = ExpiredBadgeBottomSheetDialogFragmentArgs.fromBundle(requireArguments()).badge
    val isLikelyASustainer = SignalStore.donationsValues().isLikelyASustainer()

    return configure {
      customPref(ExpiredBadge.Model(badge))

      sectionHeaderPref(
        DSLSettingsText.from(
          if (badge.isBoost()) {
            R.string.ExpiredBadgeBottomSheetDialogFragment__your_badge_has_expired
          } else {
            R.string.ExpiredBadgeBottomSheetDialogFragment__subscription_cancelled
          },
          DSLSettingsText.CenterModifier
        )
      )

      space(DimensionUnit.DP.toPixels(4f).toInt())

      noPadTextPref(
        DSLSettingsText.from(
          if (badge.isBoost()) {
            getString(R.string.ExpiredBadgeBottomSheetDialogFragment__your_boost_badge_has_expired)
          } else {
            getString(R.string.ExpiredBadgeBottomSheetDialogFragment__your_sustainer, badge.name)
          },
          DSLSettingsText.CenterModifier
        )
      )

      space(DimensionUnit.DP.toPixels(16f).toInt())

      noPadTextPref(
        DSLSettingsText.from(
          if (badge.isBoost()) {
            if (isLikelyASustainer) {
              R.string.ExpiredBadgeBottomSheetDialogFragment__you_can_reactivate
            } else {
              R.string.ExpiredBadgeBottomSheetDialogFragment__to_continue_supporting_technology
            }
          } else {
            R.string.ExpiredBadgeBottomSheetDialogFragment__you_can
          },
          DSLSettingsText.CenterModifier
        )
      )

      space(DimensionUnit.DP.toPixels(92f).toInt())

      primaryButton(
        text = DSLSettingsText.from(
          if (badge.isBoost()) {
            if (isLikelyASustainer) {
              R.string.ExpiredBadgeBottomSheetDialogFragment__add_a_boost
            } else {
              R.string.ExpiredBadgeBottomSheetDialogFragment__become_a_sustainer
            }
          } else {
            R.string.ExpiredBadgeBottomSheetDialogFragment__renew_subscription
          }
        ),
        onClick = {
          dismiss()
          if (isLikelyASustainer) {
            requireActivity().startActivity(AppSettingsActivity.boost(requireContext()))
          } else {
            requireActivity().startActivity(AppSettingsActivity.subscriptions(requireContext()))
          }
        }
      )

      secondaryButtonNoOutline(
        text = DSLSettingsText.from(R.string.ExpiredBadgeBottomSheetDialogFragment__not_now),
        onClick = {
          dismiss()
        }
      )
    }
  }

  companion object {
    @JvmStatic
    fun show(badge: Badge, fragmentManager: FragmentManager) {
      val args = ExpiredBadgeBottomSheetDialogFragmentArgs.Builder(badge).build()
      val fragment = ExpiredBadgeBottomSheetDialogFragment()
      fragment.arguments = args.toBundle()

      fragment.show(fragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
    }
  }
}
