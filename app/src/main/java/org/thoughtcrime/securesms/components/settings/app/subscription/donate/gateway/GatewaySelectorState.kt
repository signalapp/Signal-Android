package org.thoughtcrime.securesms.components.settings.app.subscription.donate.gateway

import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.util.FeatureFlags

data class GatewaySelectorState(
  val badge: Badge,
  val isGooglePayAvailable: Boolean = false,
  val isPayPalAvailable: Boolean = false,
  val isCreditCardAvailable: Boolean = FeatureFlags.creditCardPayments()
)
