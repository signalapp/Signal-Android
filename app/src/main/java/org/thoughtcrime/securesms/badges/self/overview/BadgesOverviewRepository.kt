/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.badges.self.overview

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.rx3.asFlow
import kotlinx.coroutines.rx3.await
import org.signal.core.util.logging.Log
import org.signal.network.NetworkResult
import org.thoughtcrime.securesms.badges.BadgeRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.RecurringInAppPaymentRepository
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.InternetConnectionObserver

/**
 * All of the storage and network access behind [BadgesOverviewViewModel].
 */
object BadgesOverviewRepository {

  private val TAG = Log.tag(BadgesOverviewRepository::class)

  fun observeSelf(): Flow<Recipient> {
    return Recipient.observable(Recipient.self().id).asFlow().filterNot { it.isResolving }
  }

  fun observeInternetConnection(): Flow<Boolean> {
    return InternetConnectionObserver.observe().asFlow().distinctUntilChanged()
  }

  fun getDisplayBadgesOnProfile(): Boolean = SignalStore.inAppPayments.getDisplayBadgesOnProfile()

  /**
   * The badge of a monthly donation that's been cancelled but hasn't run out yet, or null if there isn't one or the
   * service couldn't be reached.
   */
  suspend fun getFadedBadgeId(): String? {
    return try {
      val (active, all) = coroutineScope {
        val activeRequest = async { RecurringInAppPaymentRepository.getActiveSubscription(InAppPaymentSubscriberRecord.Type.DONATION).await() }
        val allRequest = async { RecurringInAppPaymentRepository.getSubscriptions().await() }
        activeRequest.await() to allRequest.await()
      }

      if (!active.isActive && active.activeSubscription?.willCancelAtPeriodEnd == true) {
        all.firstOrNull { it.level == active.activeSubscription?.level }?.badge?.id
      } else {
        null
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.w(TAG, "Could not retrieve data from server", e)
      null
    }
  }

  /** Shows or hides all of the user's badges on their profile, returning whether the service took the change. */
  suspend fun setDisplayBadgesOnProfile(displayBadgesOnProfile: Boolean): Boolean {
    val result = BadgeRepository.setVisibilityForAllBadges(displayBadgesOnProfile)
    if (result !is NetworkResult.Success) {
      Log.e(TAG, "Failed to update visibility.", result.getCause())
      return false
    }

    return true
  }
}
