/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.completed

import kotlinx.coroutines.withContext
import org.signal.core.models.badges.Badge
import org.signal.core.util.concurrent.SignalDispatchers
import org.signal.network.NetworkResult
import org.thoughtcrime.securesms.badges.Badges
import org.thoughtcrime.securesms.database.model.databaseprotos.TerminalDonationQueue
import org.thoughtcrime.securesms.net.SignalNetwork
import org.whispersystems.signalservice.api.services.DonationsService
import java.util.Locale

class TerminalDonationRepository(
  private val donationsService: DonationsService = SignalNetwork.donationsService
) {
  suspend fun getBadge(terminalDonation: TerminalDonationQueue.TerminalDonation): NetworkResult<Badge> {
    val result = withContext(SignalDispatchers.IO) {
      donationsService.getDonationsConfiguration(Locale.getDefault()).toNetworkResult()
    }

    return result.map { configuration ->
      val levelConfiguration = configuration.levels[terminalDonation.level.toInt()]!!
      Badges.fromServiceBadge(levelConfiguration.badge!!)
    }
  }
}
