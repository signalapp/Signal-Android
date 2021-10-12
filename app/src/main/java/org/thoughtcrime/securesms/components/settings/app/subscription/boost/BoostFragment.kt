package org.thoughtcrime.securesms.components.settings.app.subscription.boost

import android.text.SpannableStringBuilder
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import org.signal.core.util.DimensionUnit
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.badges.models.BadgePreview
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsAdapter
import org.thoughtcrime.securesms.components.settings.DSLSettingsBottomSheetFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsIcon
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationEvent
import org.thoughtcrime.securesms.components.settings.app.subscription.models.CurrencySelection
import org.thoughtcrime.securesms.components.settings.app.subscription.models.GooglePayButton
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.util.LifecycleDisposable
import org.thoughtcrime.securesms.util.SpanUtil

/**
 * UX to allow users to donate ephemerally.
 */
class BoostFragment : DSLSettingsBottomSheetFragment() {

  private val viewModel: BoostViewModel by viewModels(ownerProducer = { requireActivity() })
  private val lifecycleDisposable = LifecycleDisposable()

  private val sayThanks: CharSequence by lazy {
    SpannableStringBuilder(requireContext().getString(R.string.BoostFragment__say_thanks_and_earn, 30))
      .append(" ")
      .append(
        SpanUtil.learnMore(requireContext(), ContextCompat.getColor(requireContext(), R.color.signal_accent_primary)) {
          // TODO [alex] -- Where's this go?
        }
      )
  }

  override fun bindAdapter(adapter: DSLSettingsAdapter) {
    CurrencySelection.register(adapter)
    BadgePreview.register(adapter)
    Boost.register(adapter)
    GooglePayButton.register(adapter)

    viewModel.state.observe(viewLifecycleOwner) { state ->
      adapter.submitList(getConfiguration(state).toMappingModelList())
    }

    lifecycleDisposable.bindTo(viewLifecycleOwner.lifecycle)
    lifecycleDisposable += viewModel.events.subscribe { event: DonationEvent ->
      when (event) {
        is DonationEvent.GooglePayUnavailableError -> Log.w(TAG, "Google Pay error", event.throwable)
        is DonationEvent.PaymentConfirmationError -> Log.w(TAG, "Payment confirmation error", event.throwable)
        is DonationEvent.PaymentConfirmationSuccess -> onPaymentConfirmed(event.badge)
        DonationEvent.RequestTokenError -> Log.w(TAG, "Request token could not be fetched")
        DonationEvent.RequestTokenSuccess -> Log.w(TAG, "Successfully got request token from Google Pay")
        DonationEvent.SubscriptionCancelled -> Unit
      }
    }
  }

  private fun getConfiguration(state: BoostState): DSLConfiguration {
    return configure {
      customPref(BadgePreview.SubscriptionModel(state.boostBadge))

      sectionHeaderPref(
        title = DSLSettingsText.from(
          R.string.BoostFragment__give_signal_a_boost,
          DSLSettingsText.CenterModifier, DSLSettingsText.Title2BoldModifier
        )
      )

      noPadTextPref(
        title = DSLSettingsText.from(
          sayThanks,
          DSLSettingsText.CenterModifier
        )
      )

      space(DimensionUnit.DP.toPixels(28f).toInt())

      customPref(
        CurrencySelection.Model(
          currencySelection = state.currencySelection,
          isEnabled = state.stage == BoostState.Stage.READY,
          onClick = {
            findNavController().navigate(BoostFragmentDirections.actionBoostFragmentToSetDonationCurrencyFragment())
          }
        )
      )

      customPref(
        Boost.SelectionModel(
          boosts = state.boosts,
          selectedBoost = state.selectedBoost,
          currency = state.customAmount.currency,
          isCustomAmountFocused = state.isCustomAmountFocused,
          isEnabled = state.stage == BoostState.Stage.READY,
          onBoostClick = {
            viewModel.setSelectedBoost(it)
          },
          onCustomAmountChanged = {
            viewModel.setCustomAmount(it)
          },
          onCustomAmountFocusChanged = {
            viewModel.setCustomAmountFocused(it)
          }
        )
      )

      if (state.isGooglePayAvailable) {
        space(DimensionUnit.DP.toPixels(16f).toInt())

        customPref(
          GooglePayButton.Model(
            onClick = this@BoostFragment::onGooglePayButtonClicked,
            isEnabled = state.stage == BoostState.Stage.READY
          )
        )
      }

      secondaryButtonNoOutline(
        text = DSLSettingsText.from(R.string.SubscribeFragment__more_payment_options),
        icon = DSLSettingsIcon.from(R.drawable.ic_open_20, R.color.signal_accent_primary),
        onClick = {
          // TODO
        }
      )
    }
  }

  private fun onGooglePayButtonClicked() {
    viewModel.requestTokenFromGooglePay(getString(R.string.preferences__signal_boost))
  }

  private fun onPaymentConfirmed(boostBadge: Badge) {
    findNavController().navigate(BoostFragmentDirections.actionBoostFragmentToBoostThanksForYourSupportBottomSheetDialog(boostBadge).setIsBoost(true))
  }

  companion object {
    private val TAG = Log.tag(BoostFragment::class.java)
  }
}
