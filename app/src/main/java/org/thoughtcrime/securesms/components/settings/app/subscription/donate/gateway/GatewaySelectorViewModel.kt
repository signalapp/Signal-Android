package org.thoughtcrime.securesms.components.settings.app.subscription.donate.gateway

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.signal.donations.PaymentSourceType
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppDonations
import org.thoughtcrime.securesms.components.settings.app.subscription.StripeRepository
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.rx.RxStore

class GatewaySelectorViewModel(
  args: GatewaySelectorBottomSheetArgs,
  repository: StripeRepository,
  private val gatewaySelectorRepository: GatewaySelectorRepository
) : ViewModel() {

  private val store = RxStore(
    GatewaySelectorState(
      gatewayOrderStrategy = GatewayOrderStrategy.getStrategy(),
      inAppPayment = args.inAppPayment,
      isCreditCardAvailable = InAppDonations.isPaymentSourceAvailable(PaymentSourceType.Stripe.CreditCard, args.inAppPayment.type),
      isGooglePayAvailable = InAppDonations.isPaymentSourceAvailable(PaymentSourceType.Stripe.GooglePay, args.inAppPayment.type),
      isPayPalAvailable = InAppDonations.isPaymentSourceAvailable(PaymentSourceType.PayPal, args.inAppPayment.type),
      isSEPADebitAvailable = InAppDonations.isPaymentSourceAvailable(PaymentSourceType.Stripe.SEPADebit, args.inAppPayment.type),
      isIDEALAvailable = InAppDonations.isPaymentSourceAvailable(PaymentSourceType.Stripe.IDEAL, args.inAppPayment.type)
    )
  )
  private val disposables = CompositeDisposable()

  val state = store.stateFlowable

  init {
    val isGooglePayAvailable = repository.isGooglePayAvailable().toSingleDefault(true).onErrorReturnItem(false)
    val gatewayConfiguration = gatewaySelectorRepository.getAvailableGatewayConfiguration(currencyCode = args.inAppPayment.data.amount!!.currencyCode)

    disposables += Single.zip(isGooglePayAvailable, gatewayConfiguration, ::Pair).subscribeBy { (googlePayAvailable, gatewayConfiguration) ->
      SignalStore.inAppPayments.isGooglePayReady = googlePayAvailable
      store.update {
        it.copy(
          loading = false,
          isCreditCardAvailable = it.isCreditCardAvailable && gatewayConfiguration.availableGateways.contains(InAppPaymentData.PaymentMethodType.CARD),
          isGooglePayAvailable = it.isGooglePayAvailable && googlePayAvailable && gatewayConfiguration.availableGateways.contains(InAppPaymentData.PaymentMethodType.GOOGLE_PAY),
          isPayPalAvailable = it.isPayPalAvailable && gatewayConfiguration.availableGateways.contains(InAppPaymentData.PaymentMethodType.PAYPAL),
          isSEPADebitAvailable = it.isSEPADebitAvailable && gatewayConfiguration.availableGateways.contains(InAppPaymentData.PaymentMethodType.SEPA_DEBIT),
          isIDEALAvailable = it.isIDEALAvailable && gatewayConfiguration.availableGateways.contains(InAppPaymentData.PaymentMethodType.IDEAL),
          sepaEuroMaximum = gatewayConfiguration.sepaEuroMaximum
        )
      }
    }
  }

  override fun onCleared() {
    store.dispose()
    disposables.clear()
  }

  fun updateInAppPaymentMethod(inAppPaymentMethodType: InAppPaymentData.PaymentMethodType): Single<InAppPaymentTable.InAppPayment> {
    return gatewaySelectorRepository.setInAppPaymentMethodType(store.state.inAppPayment, inAppPaymentMethodType).observeOn(AndroidSchedulers.mainThread())
  }

  class Factory(
    private val args: GatewaySelectorBottomSheetArgs,
    private val repository: StripeRepository,
    private val gatewaySelectorRepository: GatewaySelectorRepository = GatewaySelectorRepository(AppDependencies.donationsService)
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(GatewaySelectorViewModel(args, repository, gatewaySelectorRepository)) as T
    }
  }
}
