package org.thoughtcrime.securesms.components.settings.app.subscription.donate.gateway

import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import org.signal.core.util.dp
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.models.BadgeDisplay112
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsAdapter
import org.thoughtcrime.securesms.components.settings.DSLSettingsBottomSheetFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsIcon
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.NO_TINT
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationPaymentComponent
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppDonations
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.DonateToSignalType
import org.thoughtcrime.securesms.components.settings.app.subscription.models.GooglePayButton
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.payments.FiatMoneyUtil
import org.thoughtcrime.securesms.util.LifecycleDisposable
import org.thoughtcrime.securesms.util.fragments.requireListener

/**
 * Entry point to capturing the necessary payment token to pay for a donation
 */
class GatewaySelectorBottomSheet : DSLSettingsBottomSheetFragment() {

  private val lifecycleDisposable = LifecycleDisposable()

  private val args: GatewaySelectorBottomSheetArgs by navArgs()

  private val viewModel: GatewaySelectorViewModel by viewModels(factoryProducer = {
    GatewaySelectorViewModel.Factory(args, requireListener<DonationPaymentComponent>().stripeRepository)
  })

  override fun bindAdapter(adapter: DSLSettingsAdapter) {
    BadgeDisplay112.register(adapter)
    GooglePayButton.register(adapter)

    lifecycleDisposable.bindTo(viewLifecycleOwner)

    lifecycleDisposable += viewModel.state.subscribe { state ->
      adapter.submitList(getConfiguration(state).toMappingModelList())
    }
  }

  private fun getConfiguration(state: GatewaySelectorState): DSLConfiguration {
    return configure {
      customPref(
        BadgeDisplay112.Model(
          badge = state.badge,
          withDisplayText = false
        )
      )

      space(12.dp)

      when (args.request.donateToSignalType) {
        DonateToSignalType.MONTHLY -> presentMonthlyText()
        DonateToSignalType.ONE_TIME -> presentOneTimeText()
      }

      space(66.dp)

      if (state.isGooglePayAvailable) {
        customPref(
          GooglePayButton.Model(
            isEnabled = true,
            onClick = {
              findNavController().popBackStack()
              val response = GatewayResponse(GatewayResponse.Gateway.GOOGLE_PAY, args.request)
              setFragmentResult(REQUEST_KEY, bundleOf(REQUEST_KEY to response))
            }
          )
        )
      }

      // PayPal

      // Credit Card
      if (InAppDonations.isCreditCardAvailable()) {
        space(12.dp)

        primaryButton(
          text = DSLSettingsText.from(R.string.GatewaySelectorBottomSheet__credit_or_debit_card),
          icon = DSLSettingsIcon.from(R.drawable.credit_card, NO_TINT),
          onClick = {
            findNavController().popBackStack()
            val response = GatewayResponse(GatewayResponse.Gateway.CREDIT_CARD, args.request)
            setFragmentResult(REQUEST_KEY, bundleOf(REQUEST_KEY to response))
          }
        )
      }

      space(16.dp)
    }
  }

  private fun DSLConfiguration.presentMonthlyText() {
    noPadTextPref(
      title = DSLSettingsText.from(
        getString(R.string.GatewaySelectorBottomSheet__donate_s_month_to_signal, FiatMoneyUtil.format(resources, args.request.fiat)),
        DSLSettingsText.CenterModifier,
        DSLSettingsText.TitleLargeModifier
      )
    )
    space(6.dp)
    noPadTextPref(
      title = DSLSettingsText.from(
        getString(R.string.GatewaySelectorBottomSheet__get_a_s_badge, args.request.badge.name),
        DSLSettingsText.CenterModifier,
        DSLSettingsText.BodyLargeModifier,
        DSLSettingsText.ColorModifier(ContextCompat.getColor(requireContext(), R.color.signal_colorOnSurfaceVariant))
      )
    )
  }

  private fun DSLConfiguration.presentOneTimeText() {
    noPadTextPref(
      title = DSLSettingsText.from(
        getString(R.string.GatewaySelectorBottomSheet__donate_s_to_signal, FiatMoneyUtil.format(resources, args.request.fiat)),
        DSLSettingsText.CenterModifier,
        DSLSettingsText.TitleLargeModifier
      )
    )
    space(6.dp)
    noPadTextPref(
      title = DSLSettingsText.from(
        resources.getQuantityString(R.plurals.GatewaySelectorBottomSheet__get_a_s_badge_for_d_days, 30, args.request.badge.name, 30),
        DSLSettingsText.CenterModifier,
        DSLSettingsText.BodyLargeModifier,
        DSLSettingsText.ColorModifier(ContextCompat.getColor(requireContext(), R.color.signal_colorOnSurfaceVariant))
      )
    )
  }

  companion object {
    const val REQUEST_KEY = "payment_checkout_mode"
  }
}
