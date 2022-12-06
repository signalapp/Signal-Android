package org.thoughtcrime.securesms.components.settings.app.subscription.receipts.detail

import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.thoughtcrime.securesms.components.settings.app.subscription.getSubscriptionLevels
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.DonationReceiptRecord
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import java.util.Locale

class DonationReceiptDetailRepository {
  fun getSubscriptionLevelName(subscriptionLevel: Int): Single<String> {
    return Single
      .fromCallable {
        ApplicationDependencies
          .getDonationsService()
          .getDonationsConfiguration(Locale.getDefault())
      }
      .flatMap { it.flattenResult() }
      .map { it.getSubscriptionLevels()[subscriptionLevel] ?: throw Exception("Subscription level $subscriptionLevel not found") }
      .map { it.name }
      .subscribeOn(Schedulers.io())
  }

  fun getDonationReceiptRecord(id: Long): Single<DonationReceiptRecord> {
    return Single.fromCallable<DonationReceiptRecord> {
      SignalDatabase.donationReceipts.getReceipt(id)
    }.subscribeOn(Schedulers.io())
  }
}
