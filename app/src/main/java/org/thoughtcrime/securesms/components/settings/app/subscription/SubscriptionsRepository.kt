package org.thoughtcrime.securesms.components.settings.app.subscription

import io.reactivex.rxjava3.core.Single
import org.signal.core.util.money.FiatMoney
import org.thoughtcrime.securesms.badges.Badges
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.subscription.Subscription
import org.thoughtcrime.securesms.util.PlatformCurrencyUtil
import org.whispersystems.signalservice.api.services.DonationsService
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription
import org.whispersystems.signalservice.api.subscriptions.SubscriptionLevels
import org.whispersystems.signalservice.internal.ServiceResponse
import java.util.Currency
import java.util.Locale

/**
 * Repository which can query for the user's active subscription as well as a list of available subscriptions,
 * in the currency indicated.
 */
class SubscriptionsRepository(private val donationsService: DonationsService) {

  fun getActiveSubscription(): Single<ActiveSubscription> {
    val localSubscription = SignalStore.donationsValues().getSubscriber()
    return if (localSubscription != null) {
      donationsService.getSubscription(localSubscription.subscriberId)
        .flatMap(ServiceResponse<ActiveSubscription>::flattenResult)
    } else {
      Single.just(ActiveSubscription(null))
    }
  }

  fun getSubscriptions(): Single<List<Subscription>> = donationsService.getSubscriptionLevels(Locale.getDefault())
    .flatMap(ServiceResponse<SubscriptionLevels>::flattenResult)
    .map { subscriptionLevels ->
      subscriptionLevels.levels.map { (code, level) ->
        Subscription(
          id = code,
          name = level.name,
          badge = Badges.fromServiceBadge(level.badge),
          prices = level.currencies.filter {
            PlatformCurrencyUtil
              .getAvailableCurrencyCodes()
              .contains(it.key)
          }.map { (currencyCode, price) ->
            FiatMoney(price, Currency.getInstance(currencyCode))
          }.toSet(),
          level = code.toInt()
        )
      }.sortedBy {
        it.level
      }
    }
}
