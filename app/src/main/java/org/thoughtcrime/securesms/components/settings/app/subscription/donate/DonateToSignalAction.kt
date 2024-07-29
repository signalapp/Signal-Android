package org.thoughtcrime.securesms.components.settings.app.subscription.donate

import org.signal.donations.InAppPaymentType
import org.thoughtcrime.securesms.database.InAppPaymentTable

sealed class DonateToSignalAction {
  data class DisplayCurrencySelectionDialog(val inAppPaymentType: InAppPaymentType, val supportedCurrencies: List<String>) : DonateToSignalAction()
  data class DisplayGatewaySelectorDialog(val inAppPayment: InAppPaymentTable.InAppPayment) : DonateToSignalAction()
  data object CancelSubscription : DonateToSignalAction()
  data class UpdateSubscription(val inAppPayment: InAppPaymentTable.InAppPayment, val isLongRunning: Boolean) : DonateToSignalAction()
}
