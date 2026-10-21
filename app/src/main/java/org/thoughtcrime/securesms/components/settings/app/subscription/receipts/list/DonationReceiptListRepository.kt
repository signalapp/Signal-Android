package org.thoughtcrime.securesms.components.settings.app.subscription.receipts.list

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.rx3.asFlow
import kotlinx.coroutines.withContext
import org.signal.core.util.concurrent.SignalDispatchers
import org.thoughtcrime.securesms.badges.Badges
import org.thoughtcrime.securesms.components.settings.app.subscription.getBoostBadges
import org.thoughtcrime.securesms.components.settings.app.subscription.getGiftBadges
import org.thoughtcrime.securesms.components.settings.app.subscription.getSubscriptionLevels
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.InAppPaymentReceiptRecord
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.util.InternetConnectionObserver
import java.util.Locale

/**
 * Reads the receipts and badges behind [DonationReceiptListViewModel].
 */
object DonationReceiptListRepository {

  /** Every donation receipt, newest first. Backup receipts are left out. */
  suspend fun getReceipts(): List<InAppPaymentReceiptRecord> {
    return withContext(SignalDispatchers.Default) {
      SignalDatabase.donationReceipts.getReceipts(null)
    }
  }

  /** Emits whether the device has a network connection, again whenever that changes. */
  fun observeIsConnected(): Flow<Boolean> {
    return InternetConnectionObserver.observe().asFlow().distinctUntilChanged()
  }

  /** The badge for each kind of donation, or none if the configuration couldn't be fetched. */
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
