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
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.badges.models.BadgePreview
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsAdapter
import org.thoughtcrime.securesms.components.settings.DSLSettingsFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsIcon
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationEvent
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationExceptions
import org.thoughtcrime.securesms.components.settings.app.subscription.models.CurrencySelection
import org.thoughtcrime.securesms.components.settings.app.subscription.models.GooglePayButton
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.subscription.Subscription
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.LifecycleDisposable
import org.thoughtcrime.securesms.util.SpanUtil
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * UX for creating and changing a subscription
 */
class SubscribeFragment : DSLSettingsFragment(
  layoutId = R.layout.subscribe_fragment
) {

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

  private lateinit var processingDonationPaymentDialog: AlertDialog

  override fun onResume() {
    super.onResume()
    viewModel.refreshActiveSubscription()
  }

  override fun bindAdapter(adapter: DSLSettingsAdapter) {
    BadgePreview.register(adapter)
    CurrencySelection.register(adapter)
    Subscription.register(adapter)
    GooglePayButton.register(adapter)

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
        is DonationEvent.GooglePayUnavailableError -> onGooglePayUnavailable(it.throwable)
        is DonationEvent.PaymentConfirmationError -> onPaymentError(it.throwable)
        is DonationEvent.PaymentConfirmationSuccess -> onPaymentConfirmed(it.badge)
        DonationEvent.RequestTokenError -> onPaymentError(null)
        DonationEvent.RequestTokenSuccess -> Log.w(TAG, "Successfully got request token from Google Pay")
        DonationEvent.SubscriptionCancelled -> onSubscriptionCancelled()
        is DonationEvent.SubscriptionCancellationFailed -> onSubscriptionFailedToCancel(it.throwable)
      }
    }
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
          currencySelection = state.currencySelection,
          isEnabled = areFieldsEnabled && state.activeSubscription?.isActive != true,
          onClick = {
            findNavController().navigate(SubscribeFragmentDirections.actionSubscribeFragmentToSetDonationCurrencyFragment(false))
          }
        )
      )

      state.subscriptions.forEach {
        val isActive = state.activeSubscription?.activeSubscription?.level == it.level
        customPref(
          Subscription.Model(
            subscription = it,
            isSelected = state.selectedSubscription == it,
            isEnabled = areFieldsEnabled,
            isActive = isActive,
            willRenew = isActive && state.activeSubscription?.activeSubscription?.willCancelAtPeriodEnd() ?: false,
            onClick = { viewModel.setSelectedSubscription(it) },
            renewalTimestamp = TimeUnit.SECONDS.toMillis(state.activeSubscription?.activeSubscription?.endOfCurrentPeriod ?: 0L)
          )
        )
      }

      if (state.activeSubscription?.isActive == true) {
        space(DimensionUnit.DP.toPixels(16f).toInt())

        val activeAndSameLevel = state.activeSubscription.isActive &&
          state.selectedSubscription?.level == state.activeSubscription.activeSubscription?.level
        val isExpiring = state.activeSubscription.isActive && state.activeSubscription.activeSubscription?.willCancelAtPeriodEnd() == true

        primaryButton(
          text = DSLSettingsText.from(R.string.SubscribeFragment__update_subscription),
          isEnabled = areFieldsEnabled && (!activeAndSameLevel || isExpiring),
          onClick = {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.MONTH, 1)
            MaterialAlertDialogBuilder(requireContext())
              .setTitle(R.string.SubscribeFragment__update_subscription_question)
              .setMessage(
                getString(
                  R.string.SubscribeFragment__you_will_be_charged_the_full_amount,
                  DateUtils.formatDateWithYear(Locale.getDefault(), calendar.timeInMillis)
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
        if (state.isGooglePayAvailable) {
          space(DimensionUnit.DP.toPixels(16f).toInt())

          customPref(
            GooglePayButton.Model(
              onClick = this@SubscribeFragment::onGooglePayButtonClicked,
              isEnabled = areFieldsEnabled && state.selectedSubscription != null
            )
          )
        }

        secondaryButtonNoOutline(
          text = DSLSettingsText.from(R.string.SubscribeFragment__more_payment_options),
          icon = DSLSettingsIcon.from(R.drawable.ic_open_20, R.color.signal_accent_primary),
          onClick = {
            // TODO [alex] support page
          }
        )
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
      Log.w(TAG, "Error occurred while redeeming token", throwable)
      MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.DonationsErrors__redemption_still_pending)
        .setMessage(R.string.DonationsErrors__you_might_not_see_your_badge_right_away)
        .setPositiveButton(android.R.string.ok) { dialog, _ ->
          dialog.dismiss()
          requireActivity().finish()
          requireActivity().startActivity(AppSettingsActivity.subscriptions(requireContext()))
        }
    } else {
      Log.w(TAG, "Error occurred while processing payment", throwable)
      MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.DonationsErrors__payment_failed)
        .setMessage(R.string.DonationsErrors__your_payment)
        .setPositiveButton(android.R.string.ok) { dialog, _ ->
          dialog.dismiss()
        }
    }
  }

  private fun onGooglePayUnavailable(throwable: Throwable?) {
    Log.w(TAG, "Google Pay error", throwable)
    MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.DonationsErrors__google_pay_unavailable)
      .setMessage(R.string.DonationsErrors__you_have_to_set_up_google_pay_to_donate_in_app)
      .setPositiveButton(android.R.string.ok) { dialog, _ ->
        dialog.dismiss()
        findNavController().popBackStack()
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
    Log.w(TAG, "Failed to cancel subscription", throwable)
    MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.DonationsErrors__failed_to_cancel_subscription)
      .setMessage(R.string.DonationsErrors__subscription_cancellation_requires_an_internet_connection)
      .setPositiveButton(android.R.string.ok) { dialog, _ ->
        dialog.dismiss()
        findNavController().popBackStack()
      }
  }

  companion object {
    private val TAG = Log.tag(SubscribeFragment::class.java)
  }
}
