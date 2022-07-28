package org.thoughtcrime.securesms.badges.gifts.flow

import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.money.FiatMoney
import org.thoughtcrime.securesms.badges.Badges
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.util.PlatformCurrencyUtil
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile
import org.whispersystems.signalservice.internal.ServiceResponse
import java.util.Currency
import java.util.Locale

/**
 * Repository for grabbing gift badges and supported currency information.
 */
class GiftFlowRepository {

  fun getGiftBadge(): Single<Pair<Long, Badge>> {
    return Single
      .fromCallable {
        ApplicationDependencies.getDonationsService()
          .getGiftBadges(Locale.getDefault())
      }
      .flatMap(ServiceResponse<Map<Long, SignalServiceProfile.Badge>>::flattenResult)
      .map { gifts -> gifts.map { it.key to Badges.fromServiceBadge(it.value) } }
      .map { it.first() }
      .subscribeOn(Schedulers.io())
  }

  fun getGiftPricing(): Single<Map<Currency, FiatMoney>> {
    return Single
      .fromCallable {
        ApplicationDependencies.getDonationsService()
          .giftAmount
      }
      .subscribeOn(Schedulers.io())
      .flatMap { it.flattenResult() }
      .map { result ->
        result
          .filter { PlatformCurrencyUtil.getAvailableCurrencyCodes().contains(it.key) }
          .mapKeys { (code, _) -> Currency.getInstance(code) }
          .mapValues { (currency, price) -> FiatMoney(price, currency) }
      }
  }
}
