package org.thoughtcrime.securesms.components.settings.app.subscription.donate.gateway

import io.reactivex.rxjava3.core.Single
import org.signal.core.util.money.FiatMoney
import org.thoughtcrime.securesms.components.settings.app.subscription.getAvailablePaymentMethods
import org.thoughtcrime.securesms.payments.currency.CurrencyUtil
import org.whispersystems.signalservice.api.services.DonationsService
import org.whispersystems.signalservice.internal.push.SubscriptionsConfiguration
import java.util.Locale

class GatewaySelectorRepository(
  private val donationsService: DonationsService
) {
  fun getAvailableGatewayConfiguration(currencyCode: String): Single<GatewayConfiguration> {
    return Single.fromCallable {
      donationsService.getDonationsConfiguration(Locale.getDefault())
    }.flatMap { it.flattenResult() }
      .map { configuration ->
        val available = configuration.getAvailablePaymentMethods(currencyCode).map {
          when (it) {
            SubscriptionsConfiguration.PAYPAL -> listOf(GatewayResponse.Gateway.PAYPAL)
            SubscriptionsConfiguration.CARD -> listOf(GatewayResponse.Gateway.CREDIT_CARD, GatewayResponse.Gateway.GOOGLE_PAY)
            SubscriptionsConfiguration.SEPA_DEBIT -> listOf(GatewayResponse.Gateway.SEPA_DEBIT)
            SubscriptionsConfiguration.IDEAL -> listOf(GatewayResponse.Gateway.IDEAL)
            else -> listOf()
          }
        }.flatten().toSet()

        GatewayConfiguration(
          availableGateways = available,
          sepaEuroMaximum = if (configuration.sepaMaximumEuros != null) FiatMoney(configuration.sepaMaximumEuros, CurrencyUtil.EURO) else null
        )
      }
  }

  data class GatewayConfiguration(
    val availableGateways: Set<GatewayResponse.Gateway>,
    val sepaEuroMaximum: FiatMoney?
  )
}
