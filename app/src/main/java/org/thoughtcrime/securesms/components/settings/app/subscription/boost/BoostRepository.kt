package org.thoughtcrime.securesms.components.settings.app.subscription.boost

import android.net.Uri
import io.reactivex.rxjava3.core.Single
import org.signal.core.util.money.FiatMoney
import org.thoughtcrime.securesms.badges.models.Badge
import java.math.BigDecimal
import java.util.Currency

class BoostRepository {

  fun getBoosts(currency: Currency): Single<Pair<List<Boost>, Boost?>> {
    val boosts = testBoosts(currency)

    return Single.just(
      Pair(
        boosts,
        boosts[2]
      )
    )
  }

  fun getBoostBadge(): Single<Badge> = Single.fromCallable {
    // Get boost badge from server
    // throw NotImplementedError()
    testBadge
  }

  companion object {
    private val testBadge = Badge(
      id = "TEST",
      category = Badge.Category.Testing,
      name = "Test Badge",
      description = "Test Badge",
      imageUrl = Uri.EMPTY,
      imageDensity = "xxxhdpi",
      expirationTimestamp = 0L,
      visible = false,
    )

    private fun testBoosts(currency: Currency) = listOf(
      3L, 5L, 10L, 20L, 50L, 100L
    ).map {
      Boost(testBadge, FiatMoney(BigDecimal.valueOf(it), currency))
    }
  }
}
