package org.thoughtcrime.securesms.components.settings.app.subscription.donate

import androidx.lifecycle.ViewModel
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.gateway.GatewayRequest
import org.whispersystems.signalservice.api.util.Preconditions

/**
 * State holder for the checkout flow when utilizing Google Pay.
 */
class DonationCheckoutViewModel : ViewModel() {

  companion object {
    private val TAG = Log.tag(DonationCheckoutViewModel::class.java)
  }

  private var gatewayRequest: GatewayRequest? = null

  fun provideGatewayRequestForGooglePay(request: GatewayRequest) {
    Log.d(TAG, "Provided with a gateway request.")
    Preconditions.checkState(gatewayRequest == null)
    gatewayRequest = request
  }

  fun consumeGatewayRequestForGooglePay(): GatewayRequest? {
    val request = gatewayRequest
    gatewayRequest = null
    return request
  }
}
