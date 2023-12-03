package org.thoughtcrime.securesms.components.settings.app.subscription.donate.gateway

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.signal.donations.PaymentSourceType
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppDonations
import org.thoughtcrime.securesms.components.settings.app.subscription.StripeRepository
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.rx.RxStore

class GatewaySelectorViewModel(
  args: GatewaySelectorBottomSheetArgs,
  repository: StripeRepository,
  gatewaySelectorRepository: GatewaySelectorRepository
) : ViewModel() {

  private val store = RxStore(
    GatewaySelectorState(
      gatewayOrderStrategy = GatewayOrderStrategy.getStrategy(),
      badge = args.request.badge,
      isGooglePayAvailable = InAppDonations.isPaymentSourceAvailable(PaymentSourceType.Stripe.GooglePay, args.request.donateToSignalType),
      isCreditCardAvailable = InAppDonations.isPaymentSourceAvailable(PaymentSourceType.Stripe.CreditCard, args.request.donateToSignalType),
      isPayPalAvailable = InAppDonations.isPaymentSourceAvailable(PaymentSourceType.PayPal, args.request.donateToSignalType),
      isSEPADebitAvailable = InAppDonations.isPaymentSourceAvailable(PaymentSourceType.Stripe.SEPADebit, args.request.donateToSignalType),
      isIDEALAvailable = InAppDonations.isPaymentSourceAvailable(PaymentSourceType.Stripe.IDEAL, args.request.donateToSignalType)
    )
  )
  private val disposables = CompositeDisposable()

  val state = store.stateFlowable

  init {
    val isGooglePayAvailable = repository.isGooglePayAvailable().toSingleDefault(true).onErrorReturnItem(false)
    val gatewayConfiguration = gatewaySelectorRepository.getAvailableGatewayConfiguration(currencyCode = args.request.currencyCode)
    disposables += Single.zip(isGooglePayAvailable, gatewayConfiguration, ::Pair).subscribeBy { (googlePayAvailable, gatewayConfiguration) ->
      SignalStore.donationsValues().isGooglePayReady = googlePayAvailable
      store.update {
        it.copy(
          loading = false,
          isCreditCardAvailable = it.isCreditCardAvailable && gatewayConfiguration.availableGateways.contains(GatewayResponse.Gateway.CREDIT_CARD),
          isGooglePayAvailable = it.isGooglePayAvailable && googlePayAvailable && gatewayConfiguration.availableGateways.contains(GatewayResponse.Gateway.GOOGLE_PAY),
          isPayPalAvailable = it.isPayPalAvailable && gatewayConfiguration.availableGateways.contains(GatewayResponse.Gateway.PAYPAL),
          isSEPADebitAvailable = it.isSEPADebitAvailable && gatewayConfiguration.availableGateways.contains(GatewayResponse.Gateway.SEPA_DEBIT),
          isIDEALAvailable = it.isIDEALAvailable && gatewayConfiguration.availableGateways.contains(GatewayResponse.Gateway.IDEAL),
          sepaEuroMaximum = gatewayConfiguration.sepaEuroMaximum
        )
      }
    }
  }

  override fun onCleared() {
    store.dispose()
    disposables.clear()
  }

  class Factory(
    private val args: GatewaySelectorBottomSheetArgs,
    private val repository: StripeRepository,
    private val gatewaySelectorRepository: GatewaySelectorRepository = GatewaySelectorRepository(ApplicationDependencies.getDonationsService())
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(GatewaySelectorViewModel(args, repository, gatewaySelectorRepository)) as T
    }
  }
}
