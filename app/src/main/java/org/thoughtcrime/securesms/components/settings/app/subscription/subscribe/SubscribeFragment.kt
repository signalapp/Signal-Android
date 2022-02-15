package org.thoughtcrime.securesms.components.settings.app.subscription.subscribe

import android.content.DialogInterface
import android.graphics.Color
import android.text.SpannableStringBuilder
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
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
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationPaymentComponent
import org.thoughtcrime.securesms.components.settings.app.subscription.SubscriptionsRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationError
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationErrorDialogs
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationErrorSource
import org.thoughtcrime.securesms.components.settings.app.subscription.models.CurrencySelection
import org.thoughtcrime.securesms.components.settings.app.subscription.models.GooglePayButton
import org.thoughtcrime.securesms.components.settings.app.subscription.models.NetworkFailure
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.components.settings.models.Progress
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.payments.FiatMoneyUtil
import org.thoughtcrime.securesms.subscription.Subscription
import org.thoughtcrime.securesms.util.LifecycleDisposable
import org.thoughtcrime.securesms.util.SpanUtil
import org.thoughtcrime.securesms.util.fragments.requireListener
import org.thoughtcrime.securesms.util.navigation.safeNavigate
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
          findNavController().safeNavigate(SubscribeFragmentDirections.actionSubscribeFragmentToSubscribeLearnMoreBottomSheetDialog())
        }
      )
  }

  private lateinit var processingDonationPaymentDialog: AlertDialog
  private lateinit var donationPaymentComponent: DonationPaymentComponent

  private var errorDialog: DialogInterface? = null

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
    donationPaymentComponent = requireListener()
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
        is DonationEvent.PaymentConfirmationSuccess -> onPaymentConfirmed(it.badge)
        DonationEvent.RequestTokenSuccess -> Log.w(TAG, "Successfully got request token from Google Pay")
        DonationEvent.SubscriptionCancelled -> onSubscriptionCancelled()
        is DonationEvent.SubscriptionCancellationFailed -> onSubscriptionFailedToCancel(it.throwable)
      }
    }
    lifecycleDisposable += donationPaymentComponent.googlePayResultPublisher.subscribe {
      viewModel.onActivityResult(it.requestCode, it.resultCode, it.data)
    }

    lifecycleDisposable += DonationError
      .getErrorsForSource(DonationErrorSource.SUBSCRIPTION)
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe { donationError ->
        onPaymentError(donationError)
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
              findNavController().safeNavigate(SubscribeFragmentDirections.actionSubscribeFragmentToSetDonationCurrencyFragment(false, selectableCurrencies.toTypedArray()))
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
    findNavController().safeNavigate(
      SubscribeFragmentDirections.actionSubscribeFragmentToSubscribeThanksForYourSupportBottomSheetDialog(badge).setIsBoost(false),
    )
  }

  private fun onPaymentError(throwable: Throwable?) {
    Log.w(TAG, "onPaymentError", throwable, true)

    if (errorDialog != null) {
      Log.i(TAG, "Already displaying an error dialog. Skipping.")
      return
    }

    errorDialog = DonationErrorDialogs.show(
      requireContext(), throwable,
      object : DonationErrorDialogs.DialogCallback() {
        override fun onDialogDismissed() {
          findNavController().popBackStack()
        }
      }
    )
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
