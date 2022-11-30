package org.thoughtcrime.securesms.components.settings.app.subscription.donate

import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.google.android.gms.wallet.PaymentData
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.signal.core.util.logging.Log
import org.signal.core.util.money.FiatMoney
import org.signal.donations.GooglePayApi
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationPaymentComponent
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppDonations
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.card.CreditCardFragment
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.card.CreditCardResult
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.gateway.GatewayRequest
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.gateway.GatewayResponse
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.gateway.GatewaySelectorBottomSheet
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.paypal.PayPalPaymentInProgressFragment
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.stripe.StripePaymentInProgressFragment
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.stripe.StripePaymentInProgressViewModel
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationError
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationErrorSource
import org.thoughtcrime.securesms.util.LifecycleDisposable
import org.thoughtcrime.securesms.util.fragments.requireListener
import java.util.Currency

/**
 * Abstracts out some common UI-level interactions between gift flow and normal donate flow.
 */
class DonationCheckoutDelegate(
  private val fragment: Fragment,
  private val callback: Callback
) : DefaultLifecycleObserver {

  companion object {
    private val TAG = Log.tag(DonationCheckoutDelegate::class.java)
  }

  private lateinit var donationPaymentComponent: DonationPaymentComponent
  private val disposables = LifecycleDisposable()
  private val viewModel: DonationCheckoutViewModel by fragment.viewModels()

  private val stripePaymentViewModel: StripePaymentInProgressViewModel by fragment.navGraphViewModels(
    R.id.donate_to_signal,
    factoryProducer = {
      donationPaymentComponent = fragment.requireListener()
      StripePaymentInProgressViewModel.Factory(donationPaymentComponent.stripeRepository)
    }
  )

  init {
    fragment.viewLifecycleOwner.lifecycle.addObserver(this)
  }

  override fun onCreate(owner: LifecycleOwner) {
    disposables.bindTo(fragment.viewLifecycleOwner)
    donationPaymentComponent = fragment.requireListener()
    registerGooglePayCallback()

    fragment.setFragmentResultListener(GatewaySelectorBottomSheet.REQUEST_KEY) { _, bundle ->
      val response: GatewayResponse = bundle.getParcelable(GatewaySelectorBottomSheet.REQUEST_KEY)!!
      handleGatewaySelectionResponse(response)
    }

    fragment.setFragmentResultListener(StripePaymentInProgressFragment.REQUEST_KEY) { _, bundle ->
      val result: DonationProcessorActionResult = bundle.getParcelable(StripePaymentInProgressFragment.REQUEST_KEY)!!
      handleDonationProcessorActionResult(result)
    }

    fragment.setFragmentResultListener(CreditCardFragment.REQUEST_KEY) { _, bundle ->
      val result: CreditCardResult = bundle.getParcelable(CreditCardFragment.REQUEST_KEY)!!
      handleCreditCardResult(result)
    }

    fragment.setFragmentResultListener(PayPalPaymentInProgressFragment.REQUEST_KEY) { _, bundle ->
      val result: DonationProcessorActionResult = bundle.getParcelable(PayPalPaymentInProgressFragment.REQUEST_KEY)!!
      handleDonationProcessorActionResult(result)
    }
  }

  private fun handleGatewaySelectionResponse(gatewayResponse: GatewayResponse) {
    when (gatewayResponse.gateway) {
      GatewayResponse.Gateway.GOOGLE_PAY -> launchGooglePay(gatewayResponse)
      GatewayResponse.Gateway.PAYPAL -> launchPayPal(gatewayResponse)
      GatewayResponse.Gateway.CREDIT_CARD -> launchCreditCard(gatewayResponse)
    }
  }

  private fun handleCreditCardResult(creditCardResult: CreditCardResult) {
    Log.d(TAG, "Received credit card information from fragment.")
    stripePaymentViewModel.provideCardData(creditCardResult.creditCardData)
    callback.navigateToStripePaymentInProgress(creditCardResult.gatewayRequest)
  }

  private fun handleDonationProcessorActionResult(result: DonationProcessorActionResult) {
    when (result.status) {
      DonationProcessorActionResult.Status.SUCCESS -> handleSuccessfulDonationProcessorActionResult(result)
      DonationProcessorActionResult.Status.FAILURE -> handleFailedDonationProcessorActionResult(result)
    }

    callback.onProcessorActionProcessed()
  }

  private fun handleSuccessfulDonationProcessorActionResult(result: DonationProcessorActionResult) {
    if (result.action == DonationProcessorAction.CANCEL_SUBSCRIPTION) {
      Snackbar.make(fragment.requireView(), R.string.SubscribeFragment__your_subscription_has_been_cancelled, Snackbar.LENGTH_LONG).show()
    } else {
      callback.onPaymentComplete(result.request)
    }
  }

  private fun handleFailedDonationProcessorActionResult(result: DonationProcessorActionResult) {
    if (result.action == DonationProcessorAction.CANCEL_SUBSCRIPTION) {
      MaterialAlertDialogBuilder(fragment.requireContext())
        .setTitle(R.string.DonationsErrors__failed_to_cancel_subscription)
        .setMessage(R.string.DonationsErrors__subscription_cancellation_requires_an_internet_connection)
        .setPositiveButton(android.R.string.ok) { _, _ ->
          fragment.findNavController().popBackStack()
        }
        .show()
    } else {
      Log.w(TAG, "Stripe action failed: ${result.action}")
    }
  }

  private fun launchPayPal(gatewayResponse: GatewayResponse) {
    if (InAppDonations.isPayPalAvailable()) {
      callback.navigateToPayPalPaymentInProgress(gatewayResponse.request)
    } else {
      error("PayPal is not currently enabled.")
    }
  }

  private fun launchGooglePay(gatewayResponse: GatewayResponse) {
    viewModel.provideGatewayRequestForGooglePay(gatewayResponse.request)
    donationPaymentComponent.stripeRepository.requestTokenFromGooglePay(
      price = FiatMoney(gatewayResponse.request.price, Currency.getInstance(gatewayResponse.request.currencyCode)),
      label = gatewayResponse.request.label,
      requestCode = gatewayResponse.request.donateToSignalType.requestCode.toInt()
    )
  }

  private fun launchCreditCard(gatewayResponse: GatewayResponse) {
    if (InAppDonations.isCreditCardAvailable()) {
      callback.navigateToCreditCardForm(gatewayResponse.request)
    } else {
      error("Credit cards are not currently enabled.")
    }
  }

  private fun registerGooglePayCallback() {
    disposables += donationPaymentComponent.googlePayResultPublisher.subscribeBy(
      onNext = { paymentResult ->
        viewModel.consumeGatewayRequestForGooglePay()?.let {
          donationPaymentComponent.stripeRepository.onActivityResult(
            paymentResult.requestCode,
            paymentResult.resultCode,
            paymentResult.data,
            paymentResult.requestCode,
            GooglePayRequestCallback(it)
          )
        }
      }
    )
  }

  inner class GooglePayRequestCallback(private val request: GatewayRequest) : GooglePayApi.PaymentRequestCallback {
    override fun onSuccess(paymentData: PaymentData) {
      Log.d(TAG, "Successfully retrieved payment data from Google Pay", true)
      stripePaymentViewModel.providePaymentData(paymentData)
      callback.navigateToStripePaymentInProgress(request)
    }

    override fun onError(googlePayException: GooglePayApi.GooglePayException) {
      Log.w(TAG, "Failed to retrieve payment data from Google Pay", googlePayException, true)

      val error = DonationError.getGooglePayRequestTokenError(
        source = when (request.donateToSignalType) {
          DonateToSignalType.MONTHLY -> DonationErrorSource.SUBSCRIPTION
          DonateToSignalType.ONE_TIME -> DonationErrorSource.BOOST
          DonateToSignalType.GIFT -> DonationErrorSource.GIFT
        },
        throwable = googlePayException
      )

      DonationError.routeDonationError(fragment.requireContext(), error)
    }

    override fun onCancelled() {
      Log.d(TAG, "Cancelled Google Pay.", true)
    }
  }

  interface Callback {
    fun navigateToStripePaymentInProgress(gatewayRequest: GatewayRequest)
    fun navigateToPayPalPaymentInProgress(gatewayRequest: GatewayRequest)
    fun navigateToCreditCardForm(gatewayRequest: GatewayRequest)
    fun onPaymentComplete(gatewayRequest: GatewayRequest)
    fun onProcessorActionProcessed()
  }
}
