package org.thoughtcrime.securesms.components.settings.app.subscription.donate.gateway

import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.signal.donations.PaymentSourceType
import org.thoughtcrime.securesms.components.settings.app.subscription.GooglePayRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppDonations
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.rx.RxStore

class GatewaySelectorViewModel(
  args: GatewaySelectorBottomSheetArgs,
  repository: GooglePayRepository
) : ViewModel() {

  private val store = RxStore<GatewaySelectorState>(GatewaySelectorState.Loading)
  private val disposables = CompositeDisposable()

  val state = store.stateFlowable

  init {
    val inAppPayment = InAppPaymentsRepository.requireInAppPayment(args.inAppPaymentId)
    val isGooglePayAvailable = repository.isGooglePayAvailable().toSingleDefault(true).onErrorReturnItem(false)
    val gatewayConfiguration = inAppPayment.flatMap { GatewaySelectorRepository.getAvailableGatewayConfiguration(currencyCode = it.data.amount!!.currencyCode) }

    disposables += Single.zip(inAppPayment, isGooglePayAvailable, gatewayConfiguration, ::Triple).subscribeBy { (inAppPayment, googlePayAvailable, gatewayConfiguration) ->
      SignalStore.inAppPayments.isGooglePayReady = googlePayAvailable
      store.update {
        GatewaySelectorState.Ready(
          gatewayOrderStrategy = GatewayOrderStrategy.getStrategy(),
          inAppPayment = inAppPayment,
          isCreditCardAvailable = InAppDonations.isDonationsPaymentSourceAvailable(PaymentSourceType.Stripe.CreditCard, inAppPayment.type) && gatewayConfiguration.availableGateways.contains(InAppPaymentData.PaymentMethodType.CARD),
          isGooglePayAvailable = InAppDonations.isDonationsPaymentSourceAvailable(PaymentSourceType.Stripe.GooglePay, inAppPayment.type) && googlePayAvailable && gatewayConfiguration.availableGateways.contains(InAppPaymentData.PaymentMethodType.GOOGLE_PAY),
          isPayPalAvailable = InAppDonations.isDonationsPaymentSourceAvailable(PaymentSourceType.PayPal, inAppPayment.type) && gatewayConfiguration.availableGateways.contains(InAppPaymentData.PaymentMethodType.PAYPAL),
          isSEPADebitAvailable = InAppDonations.isDonationsPaymentSourceAvailable(PaymentSourceType.Stripe.SEPADebit, inAppPayment.type) && gatewayConfiguration.availableGateways.contains(InAppPaymentData.PaymentMethodType.SEPA_DEBIT),
          isIDEALAvailable = InAppDonations.isDonationsPaymentSourceAvailable(PaymentSourceType.Stripe.IDEAL, inAppPayment.type) && gatewayConfiguration.availableGateways.contains(InAppPaymentData.PaymentMethodType.IDEAL),
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
    val state = store.state as GatewaySelectorState.Ready

    return GatewaySelectorRepository.setInAppPaymentMethodType(state.inAppPayment, inAppPaymentMethodType).observeOn(AndroidSchedulers.mainThread())
  }
}
