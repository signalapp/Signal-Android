package org.thoughtcrime.securesms.badges.self.expired

import androidx.core.content.ContextCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsAdapter
import org.thoughtcrime.securesms.components.settings.DSLSettingsBottomSheetFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.components.settings.models.SplashImage
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.CommunicationActions

class CantProcessSubscriptionPaymentBottomSheetDialogFragment : DSLSettingsBottomSheetFragment() {
  override fun bindAdapter(adapter: DSLSettingsAdapter) {
    SplashImage.register(adapter)
    adapter.submitList(getConfiguration().toMappingModelList())
  }

  private fun getConfiguration(): DSLConfiguration {
    return configure {
      customPref(SplashImage.Model(R.drawable.ic_card_process))

      sectionHeaderPref(
        title = DSLSettingsText.from(R.string.CantProcessSubscriptionPaymentBottomSheetDialogFragment__cant_process_subscription_payment, DSLSettingsText.CenterModifier)
      )

      textPref(
        summary = DSLSettingsText.from(
          requireContext().getString(R.string.CantProcessSubscriptionPaymentBottomSheetDialogFragment__were_having_trouble),
          DSLSettingsText.LearnMoreModifier(ContextCompat.getColor(requireContext(), R.color.signal_accent_primary)) {
            CommunicationActions.openBrowserLink(requireContext(), requireContext().getString(R.string.donation_decline_code_error_url))
          },
          DSLSettingsText.CenterModifier
        )
      )

      primaryButton(
        text = DSLSettingsText.from(android.R.string.ok)
      ) {
        dismissAllowingStateLoss()
      }

      secondaryButtonNoOutline(
        text = DSLSettingsText.from(R.string.CantProcessSubscriptionPaymentBottomSheetDialogFragment__dont_show_this_again)
      ) {
        SignalStore.donationsValues().showCantProcessDialog = false
      }
    }
  }
}
