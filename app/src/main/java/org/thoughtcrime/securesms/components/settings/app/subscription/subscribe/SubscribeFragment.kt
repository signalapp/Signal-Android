package org.thoughtcrime.securesms.components.settings.app.subscription.subscribe

import android.text.SpannableStringBuilder
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import org.signal.core.util.DimensionUnit
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.badges.models.BadgePreview
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsAdapter
import org.thoughtcrime.securesms.components.settings.DSLSettingsFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsIcon
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationEvent
import org.thoughtcrime.securesms.components.settings.app.subscription.models.CurrencySelection
import org.thoughtcrime.securesms.components.settings.app.subscription.models.GooglePayButton
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.subscription.Subscription
import org.thoughtcrime.securesms.util.LifecycleDisposable
import org.thoughtcrime.securesms.util.SpanUtil

/**
 * UX for creating and changing a subscription
 */
class SubscribeFragment : DSLSettingsFragment() {

  private val viewModel: SubscribeViewModel by viewModels(ownerProducer = { requireActivity() })

  private val lifecycleDisposable = LifecycleDisposable()

  private val supportTechSummary: CharSequence by lazy {
    SpannableStringBuilder(requireContext().getString(R.string.SubscribeFragment__support_technology_that_is_built_for_you))
      .append(" ")
      .append(
        SpanUtil.learnMore(requireContext(), ContextCompat.getColor(requireContext(), R.color.signal_accent_primary)) {
          findNavController().navigate(SubscribeFragmentDirections.actionSubscribeFragmentToSubscribeLearnMoreBottomSheetDialog())
        }
      )
  }

  override fun onResume() {
    super.onResume()
    viewModel.refresh()
  }

  override fun bindAdapter(adapter: DSLSettingsAdapter) {
    BadgePreview.register(adapter)
    CurrencySelection.register(adapter)
    Subscription.register(adapter)
    GooglePayButton.register(adapter)

    viewModel.state.observe(viewLifecycleOwner) { state ->
      adapter.submitList(getConfiguration(state).toMappingModelList())
    }

    lifecycleDisposable.bindTo(viewLifecycleOwner.lifecycle)
    lifecycleDisposable += viewModel.events.subscribe {
      when (it) {
        is DonationEvent.GooglePayUnavailableError -> Log.w(TAG, "Google Pay error", it.throwable)
        is DonationEvent.PaymentConfirmationError -> Log.w(TAG, "Payment confirmation error", it.throwable)
        is DonationEvent.PaymentConfirmationSuccess -> onPaymentConfirmed(it.badge)
        DonationEvent.RequestTokenError -> Log.w(TAG, "Request token could not be fetched")
        DonationEvent.RequestTokenSuccess -> Log.w(TAG, "Successfully got request token from Google Pay")
        DonationEvent.SubscriptionCancelled -> onSubscriptionCancelled()
      }
    }
  }

  private fun getConfiguration(state: SubscribeState): DSLConfiguration {
    return configure {
      customPref(BadgePreview.SubscriptionModel(state.previewBadge))

      sectionHeaderPref(
        title = DSLSettingsText.from(
          R.string.SubscribeFragment__signal_is_powered_by_people_like_you,
          DSLSettingsText.CenterModifier, DSLSettingsText.Title2BoldModifier
        )
      )

      noPadTextPref(
        title = DSLSettingsText.from(supportTechSummary, DSLSettingsText.CenterModifier)
      )

      space(DimensionUnit.DP.toPixels(16f).toInt())

      customPref(
        CurrencySelection.Model(
          currencySelection = state.currencySelection,
          isEnabled = state.stage == SubscribeState.Stage.READY,
          onClick = {
            findNavController().navigate(SubscribeFragmentDirections.actionSubscribeFragmentToSetDonationCurrencyFragment())
          }
        )
      )

      state.subscriptions.forEach {
        customPref(
          Subscription.Model(
            subscription = it,
            isSelected = state.selectedSubscription == it,
            isEnabled = state.stage == SubscribeState.Stage.READY,
            isActive = state.activeSubscription == it,
            onClick = { viewModel.setSelectedSubscription(it) }
          )
        )
      }

      if (state.activeSubscription != null) {
        primaryButton(
          text = DSLSettingsText.from(R.string.SubscribeFragment__update_subscription),
          onClick = {
            // TODO [alex] -- Dunno what the update process requires.
          }
        )

        secondaryButtonNoOutline(
          text = DSLSettingsText.from(R.string.SubscribeFragment__cancel_subscription),
          onClick = {
            MaterialAlertDialogBuilder(requireContext())
              .setTitle(R.string.SubscribeFragment__confirm_cancellation)
              .setMessage(R.string.SubscribeFragment__you_wont_be_charged_again)
              .setPositiveButton(R.string.SubscribeFragment__confirm) { d, _ ->
                d.dismiss()
                viewModel.cancel()
              }
              .setNegativeButton(R.string.SubscribeFragment__not_now) { d, _ ->
                d.dismiss()
              }
              .show()
          }
        )
      } else {
        if (state.isGooglePayAvailable) {
          space(DimensionUnit.DP.toPixels(16f).toInt())

          customPref(
            GooglePayButton.Model(
              onClick = this@SubscribeFragment::onGooglePayButtonClicked,
              isEnabled = state.stage == SubscribeState.Stage.READY
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
  }

  private fun onGooglePayButtonClicked() {
    viewModel.requestTokenFromGooglePay()
  }

  private fun onPaymentConfirmed(badge: Badge) {
    findNavController().navigate(SubscribeFragmentDirections.actionSubscribeFragmentToSubscribeThanksForYourSupportBottomSheetDialog(badge).setIsBoost(false))
  }

  private fun onSubscriptionCancelled() {
    Snackbar.make(requireView(), R.string.SubscribeFragment__your_subscription_has_been_cancelled, Snackbar.LENGTH_LONG).show()
  }

  companion object {
    private val TAG = Log.tag(SubscribeFragment::class.java)
  }
}
