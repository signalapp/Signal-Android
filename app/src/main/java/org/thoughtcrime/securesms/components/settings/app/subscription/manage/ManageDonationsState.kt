package org.thoughtcrime.securesms.components.settings.app.subscription.manage

import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.subscription.Subscription

data class ManageDonationsState(
  val featuredBadge: Badge? = null,
  val activeSubscription: Subscription? = null
)
