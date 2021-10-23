package org.thoughtcrime.securesms.subscription

import org.whispersystems.signalservice.api.subscriptions.SubscriberId

data class Subscriber(
  val subscriberId: SubscriberId,
  val currencyCode: String
)
