package org.thoughtcrime.securesms.components.settings.app.subscription.donate.gateway

import org.thoughtcrime.securesms.badges.models.Badge

data class GatewaySelectorState(
  val badge: Badge,
  val isGooglePayAvailable: Boolean = false
)
