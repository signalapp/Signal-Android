/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.completed

import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.thoughtcrime.securesms.badges.Badges
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.database.model.databaseprotos.DonationCompletedQueue
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.whispersystems.signalservice.api.services.DonationsService
import java.util.Locale

class DonationCompletedRepository(
  private val donationsService: DonationsService = ApplicationDependencies.getDonationsService()
) {
  fun getBadge(donationCompleted: DonationCompletedQueue.DonationCompleted): Single<Badge> {
    return Single
      .fromCallable { donationsService.getDonationsConfiguration(Locale.getDefault()) }
      .flatMap { it.flattenResult() }
      .map { it.levels[donationCompleted.level.toInt()]!! }
      .map { Badges.fromServiceBadge(it.badge) }
      .subscribeOn(Schedulers.io())
  }
}
