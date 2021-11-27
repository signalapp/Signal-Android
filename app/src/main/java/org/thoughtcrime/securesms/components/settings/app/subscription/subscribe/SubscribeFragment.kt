package org.thoughtcrime.securesms.components.settings.app.subscription.subscribe

import android.graphics.Color
import android.text.SpannableStringBuilder
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import org.signal.core.util.DimensionUnit
import org.signal.core.util.logging.Log
import org.signal.core.util.money.FiatMoney
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.badges.models.BadgePreview
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsAdapter
import org.thoughtcrime.securesms.components.settings.DSLSettingsFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationEvent
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationExceptions
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationPaymentComponent
import org.thoughtcrime.securesms.components.settings.app.subscription.SubscriptionsRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.models.CurrencySelection
import org.thoughtcrime.securesms.components.settings.app.subscription.models.GooglePayButton
import org.thoughtcrime.securesms.components.settings.app.subscription.models.NetworkFailure
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.components.settings.models.Progress
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.help.HelpFragment
import org.thoughtcrime.securesms.keyboard.findListener
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.payments.FiatMoneyUtil
import org.thoughtcrime.securesms.subscription.Subscription
import org.thoughtcrime.securesms.util.LifecycleDisposable
import org.thoughtcrime.securesms.util.SpanUtil
import java.util.Calendar
import java.util.Currency
import java.util.concurrent.TimeUnit

/**
 * UX for creating and changing a subscription
 */
class SubscribeFragment : DSLSettingsFragment(
  layoutId = R.layout.subscribe_fragment
) {

  private val lifecycleDisposable = LifecycleDisposable()

  private val supportTechSummary: CharSequence by lazy {
    SpannableStringBuilder(requireContext().getString(R.string.SubscribeFragment__support_technology_that_is_built_for_you_not))
      .append(" ")
      .append(
        SpanUtil.readMore(requireContext(), ContextCompat.getColor(requireContext(), R.color.signal_button_secondary_text)) {
          findNavController().navigate(SubscribeFragmentDirections.actionSubscribeFragmentToSubscribeLearnMoreBottomSheetDialog())
        }
      )
  }

  private lateinit var processingDonationPaymentDialog: AlertDialog
  private lateinit var donationPaymentComponent: DonationPaymentComponent

  private val viewModel: SubscribeViewModel by viewModels(
    factoryProducer = {
      SubscribeViewModel.Factory(SubscriptionsRepository(ApplicationDependencies.getDonationsService()), donationPaymentComponent.donationPaymentRepository, FETCH_SUBSCRIPTION_TOKEN_REQUEST_CODE)
    }
  )

  override fun onResume() {
    super.onResume()
    viewModel.refreshActiveSubscription()
  }

  override fun bindAdapter(adapter: DSLSettingsAdapter) {
    donationPaymentComponent = findListener()!!
    viewModel.refresh()

    BadgePreview.register(adapter)
    CurrencySelection.register(adapter)
    Subscription.register(adapter)
    GooglePayButton.register(adapter)
    Progress.register(adapter)
    NetworkFailure.register(adapter)

    processingDonationPaymentDialog = MaterialAlertDialogBuilder(requireContext())
      .setView(R.layout.processing_payment_dialog)
      .setCancelable(false)
      .create()

    viewModel.state.observe(viewLifecycleOwner) { state ->
      adapter.submitList(getConfiguration(state).toMappingModelList())
    }

    lifecycleDisposable.bindTo(viewLifecycleOwner.lifecycle)
    lifecycleDisposable += viewModel.events.subscribe {
      when (it) {
        is DonationEvent.GooglePayUnavailableError -> Unit
        is DonationEvent.PaymentConfirmationError -> onPaymentError(it.throwable)
        is DonationEvent.PaymentConfirmationSuccess -> onPaymentConfirmed(it.badge)
        is DonationEvent.RequestTokenError -> onPaymentError(DonationExceptions.SetupFailed(it.throwable))
        DonationEvent.RequestTokenSuccess -> Log.w(TAG, "Successfully got request token from Google Pay")
        DonationEvent.SubscriptionCancelled -> onSubscriptionCancelled()
        is DonationEvent.SubscriptionCancellationFailed -> onSubscriptionFailedToCancel(it.throwable)
      }
    }
    lifecycleDisposable += donationPaymentComponent.googlePayResultPublisher.subscribe {
      viewModel.onActivityResult(it.requestCode, it.resultCode, it.data)
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    processingDonationPaymentDialog.hide()
  }

  private fun getConfiguration(state: SubscribeState): DSLConfiguration {
    if (state.hasInProgressSubscriptionTransaction || state.stage == SubscribeState.Stage.PAYMENT_PIPELINE) {
      processingDonationPaymentDialog.show()
    } else {
      processingDonationPaymentDialog.hide()
    }

    val areFieldsEnabled = state.stage == SubscribeState.Stage.READY && !state.hasInProgressSubscriptionTransaction

    return configure {
      customPref(BadgePreview.SubscriptionModel(state.selectedSubscription?.badge))

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
          selectedCurrency = state.currencySelection,
          isEnabled = areFieldsEnabled && state.activeSubscription?.isActive != true,
          onClick = {
            val selectableCurrencies = viewModel.getSelectableCurrencyCodes()
            if (selectableCurrencies != null) {
              findNavController().navigate(SubscribeFragmentDirections.actionSubscribeFragmentToSetDonationCurrencyFragment(false, selectableCurrencies.toTypedArray()))
            }
          }
        )
      )

      space(DimensionUnit.DP.toPixels(4f).toInt())

      @Suppress("CascadeIf")
      if (state.stage == SubscribeState.Stage.INIT) {
        customPref(
          Subscription.LoaderModel()
        )
      } else if (state.stage == SubscribeState.Stage.FAILURE) {
        space(DimensionUnit.DP.toPixels(69f).toInt())
        customPref(
          NetworkFailure.Model {
            viewModel.refresh()
          }
        )
        space(DimensionUnit.DP.toPixels(75f).toInt())
      } else {
        state.subscriptions.forEach {

          val isActive = state.activeSubscription?.activeSubscription?.level == it.level && state.activeSubscription.isActive

          val activePrice = state.activeSubscription?.activeSubscription?.let { sub ->
            val activeCurrency = Currency.getInstance(sub.currency)
            val activeAmount = sub.amount.movePointLeft(activeCurrency.defaultFractionDigits)

            FiatMoney(activeAmount, activeCurrency)
          }

          customPref(
            Subscription.Model(
              activePrice = if (isActive) activePrice else null,
              subscription = it,
              isSelected = state.selectedSubscription == it,
              isEnabled = areFieldsEnabled,
              isActive = isActive,
              willRenew = isActive && !state.isSubscriptionExpiring(),
              onClick = { viewModel.setSelectedSubscription(it) },
              renewalTimestamp = TimeUnit.SECONDS.toMillis(state.activeSubscription?.activeSubscription?.endOfCurrentPeriod ?: 0L),
              selectedCurrency = state.currencySelection
            )
          )
        }
      }

      if (state.activeSubscription?.isActive == true) {
        space(DimensionUnit.DP.toPixels(16f).toInt())

        val activeAndSameLevel = state.activeSubscription.isActive &&
          state.selectedSubscription?.level == state.activeSubscription.activeSubscription?.level

        primaryButton(
          text = DSLSettingsText.from(R.string.SubscribeFragment__update_subscription),
          isEnabled = areFieldsEnabled && (!activeAndSameLevel || state.isSubscriptionExpiring()),
          onClick = {
            val price = viewModel.getPriceOfSelectedSubscription() ?: return@primaryButton
            val calendar = Calendar.getInstance()

            calendar.add(Calendar.MONTH, 1)
            MaterialAlertDialogBuilder(requireContext())
              .setTitle(R.string.SubscribeFragment__update_subscription_question)
              .setMessage(
                getString(
                  R.string.SubscribeFragment__you_will_be_charged_the_full_amount_s_of,
                  FiatMoneyUtil.format(
                    requireContext().resources,
                    price,
                    FiatMoneyUtil.formatOptions().trimZerosAfterDecimal()
                  )
                )
              )
              .setPositiveButton(R.string.SubscribeFragment__update) { dialog, _ ->
                dialog.dismiss()
                viewModel.updateSubscription()
              }
              .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.dismiss()
              }
              .show()
          }
        )

        secondaryButtonNoOutline(
          text = DSLSettingsText.from(R.string.SubscribeFragment__cancel_subscription),
          isEnabled = areFieldsEnabled,
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
        space(DimensionUnit.DP.toPixels(16f).toInt())

        customPref(
          GooglePayButton.Model(
            onClick = this@SubscribeFragment::onGooglePayButtonClicked,
            isEnabled = areFieldsEnabled && state.selectedSubscription != null
          )
        )

        space(DimensionUnit.DP.toPixels(8f).toInt())
      }
    }
  }

  private fun onGooglePayButtonClicked() {
    viewModel.requestTokenFromGooglePay()
  }

  private fun onPaymentConfirmed(badge: Badge) {
    findNavController().navigate(
      SubscribeFragmentDirections.actionSubscribeFragmentToSubscribeThanksForYourSupportBottomSheetDialog(badge).setIsBoost(false),
    )
  }

  private fun onPaymentError(throwable: Throwable?) {
    if (throwable is DonationExceptions.TimedOutWaitingForTokenRedemption) {
      Log.w(TAG, "Timeout occurred while redeeming token", throwable, true)
      MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.DonationsErrors__still_processing)
        .setMessage(R.string.DonationsErrors__your_payment_is_still)
        .setPositiveButton(android.R.string.ok) { dialog, _ ->
          dialog.dismiss()
          requireActivity().finish()
          requireActivity().startActivity(AppSettingsActivity.manageSubscriptions(requireContext()))
        }
        .show()
    } else if (throwable is DonationExceptions.SetupFailed) {
      Log.w(TAG, "Error occurred while processing payment", throwable, true)
      MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.DonationsErrors__error_processing_payment)
        .setMessage(R.string.DonationsErrors__your_payment)
        .setPositiveButton(android.R.string.ok) { dialog, _ ->
          dialog.dismiss()
        }
        .show()
    } else if (SignalStore.donationsValues().shouldCancelSubscriptionBeforeNextSubscribeAttempt) {
      Log.w(TAG, "Stripe failed to process payment", throwable, true)
      MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.DonationsErrors__error_processing_payment)
        .setMessage(R.string.DonationsErrors__your_badge_could_not_be_added)
        .setPositiveButton(R.string.Subscription__contact_support) { dialog, _ ->
          dialog.dismiss()
          requireActivity().finish()
          requireActivity().startActivity(AppSettingsActivity.help(requireContext(), HelpFragment.DONATION_INDEX))
        }
        .show()
    } else {
      Log.w(TAG, "Error occurred while trying to redeem token", throwable, true)
      MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.DonationsErrors__couldnt_add_badge)
        .setMessage(R.string.DonationsErrors__your_badge_could_not)
        .setPositiveButton(R.string.Subscription__contact_support) { dialog, _ ->
          dialog.dismiss()
          requireActivity().finish()
          requireActivity().startActivity(AppSettingsActivity.help(requireContext(), HelpFragment.DONATION_INDEX))
        }
        .show()
    }
  }

  private fun onSubscriptionCancelled() {
    Snackbar.make(requireView(), R.string.SubscribeFragment__your_subscription_has_been_cancelled, Snackbar.LENGTH_LONG)
      .setTextColor(Color.WHITE)
      .show()

    requireActivity().finish()
    requireActivity().startActivity(AppSettingsActivity.home(requireContext()))
  }

  private fun onSubscriptionFailedToCancel(throwable: Throwable) {
    Log.w(TAG, "Failed to cancel subscription", throwable, true)
    MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.DonationsErrors__failed_to_cancel_subscription)
      .setMessage(R.string.DonationsErrors__subscription_cancellation_requires_an_internet_connection)
      .setPositiveButton(android.R.string.ok) { dialog, _ ->
        dialog.dismiss()
        findNavController().popBackStack()
      }
      .show()
  }

  companion object {
    private val TAG = Log.tag(SubscribeFragment::class.java)
    private const val FETCH_SUBSCRIPTION_TOKEN_REQUEST_CODE = 1000
  }
}
