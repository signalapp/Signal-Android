package org.thoughtcrime.securesms.components.settings.app.subscription

import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import org.thoughtcrime.securesms.subscription.Subscription
import java.util.Currency

/**
 * Repository which can query for the user's active subscription as well as a list of available subscriptions,
 * in the currency indicated.
 */
class SubscriptionsRepository {

  fun getActiveSubscription(currency: Currency): Maybe<Subscription> = Maybe.empty()

  fun getSubscriptions(currency: Currency): Single<List<Subscription>> = Single.fromCallable {
    listOf()
  }
}
