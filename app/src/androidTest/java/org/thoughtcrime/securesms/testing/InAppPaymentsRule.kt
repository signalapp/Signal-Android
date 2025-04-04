/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.testing

import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.every
import org.junit.rules.ExternalResource
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.util.JsonUtils
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.internal.push.SubscriptionsConfiguration

/**
 * Sets up some common infrastructure for on-device InAppPayment testing
 */
class InAppPaymentsRule : ExternalResource() {
  override fun before() {
    initialiseConfigurationResponse()
    initialisePutSubscription()
    initialiseSetArchiveBackupId()
  }

  private fun initialiseConfigurationResponse() {
    val assets = InstrumentationRegistry.getInstrumentation().context.resources.assets
    val response = assets.open("inAppPaymentsTests/configuration.json").use { stream ->
      NetworkResult.Success(JsonUtils.fromJson(stream, SubscriptionsConfiguration::class.java))
    }

    AppDependencies.donationsApi.apply {
      every { getDonationsConfiguration(any()) } returns response
    }
  }

  private fun initialisePutSubscription() {
    AppDependencies.donationsApi.apply {
      every { putSubscription(any()) } returns NetworkResult.Success(Unit)
    }
  }

  private fun initialiseSetArchiveBackupId() {
    AppDependencies.archiveApi.apply {
      every { triggerBackupIdReservation(any(), any(), any()) } returns NetworkResult.Success(Unit)
    }
  }
}
