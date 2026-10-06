package org.thoughtcrime.securesms.components.settings.app.subscription.receipts.list

import kotlinx.coroutines.withContext
import org.signal.core.util.concurrent.SignalDispatchers
import org.thoughtcrime.securesms.badges.Badges
import org.thoughtcrime.securesms.components.settings.app.subscription.getBoostBadges
import org.thoughtcrime.securesms.components.settings.app.subscription.getGiftBadges
import org.thoughtcrime.securesms.components.settings.app.subscription.getSubscriptionLevels
import org.thoughtcrime.securesms.database.model.InAppPaymentReceiptRecord
import org.thoughtcrime.securesms.net.SignalNetwork
import java.util.Locale

object DonationReceiptListRepository {
  suspend fun getBadges(): List<DonationReceiptBadge> {
    return withContext(SignalDispatchers.IO) {
      val response = SignalNetwork.donationsService
        .getDonationsConfiguration(Locale.getDefault())

      if (response.result.isPresent) {
        val config = response.result.get()
        val boostBadge = DonationReceiptBadge(InAppPaymentReceiptRecord.Type.ONE_TIME_DONATION, -1, config.getBoostBadges().first())
        val giftBadge = DonationReceiptBadge(InAppPaymentReceiptRecord.Type.ONE_TIME_GIFT, -1, config.getGiftBadges().first())
        val subBadges = config.getSubscriptionLevels().map {
          DonationReceiptBadge(
            level = it.key,
            badge = Badges.fromServiceBadge(it.value.badge!!),
            type = InAppPaymentReceiptRecord.Type.RECURRING_DONATION
          )
        }
        subBadges + boostBadge + giftBadge
      } else {
        emptyList()
      }
    }
  }
}
