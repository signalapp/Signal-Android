package org.thoughtcrime.securesms.components.settings.app.subscription.donate.gateway

import io.reactivex.rxjava3.core.Single
import org.thoughtcrime.securesms.components.settings.app.subscription.getAvailablePaymentMethods
import org.whispersystems.signalservice.api.services.DonationsService
import java.util.Locale

class GatewaySelectorRepository(
  private val donationsService: DonationsService
) {
  fun getAvailableGateways(currencyCode: String): Single<Set<GatewayResponse.Gateway>> {
    return Single.fromCallable {
      donationsService.getDonationsConfiguration(Locale.getDefault())
    }.flatMap { it.flattenResult() }
      .map { configuration ->
        configuration.getAvailablePaymentMethods(currencyCode).map {
          when (it) {
            "PAYPAL" -> listOf(GatewayResponse.Gateway.PAYPAL)
            "CARD" -> listOf(GatewayResponse.Gateway.CREDIT_CARD, GatewayResponse.Gateway.GOOGLE_PAY)
            else -> listOf()
          }
        }.flatten().toSet()
      }
  }
}
