/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.testing

import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.mockwebserver.MockResponse
import org.junit.rules.ExternalResource
import org.thoughtcrime.securesms.dependencies.InstrumentationApplicationDependencyProvider
import org.thoughtcrime.securesms.util.JsonUtils
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
    InstrumentationApplicationDependencyProvider.addMockWebRequestHandlers(
      Get("/v1/subscription/configuration") {
        val assets = InstrumentationRegistry.getInstrumentation().context.resources.assets
        assets.open("inAppPaymentsTests/configuration.json").use { stream ->
          MockResponse().success(JsonUtils.fromJson(stream, SubscriptionsConfiguration::class.java))
        }
      }
    )
  }

  private fun initialisePutSubscription() {
    InstrumentationApplicationDependencyProvider.addMockWebRequestHandlers(
      Put("/v1/subscription/") {
        MockResponse().success()
      }
    )
  }

  private fun initialiseSetArchiveBackupId() {
    InstrumentationApplicationDependencyProvider.addMockWebRequestHandlers(
      Put("/v1/archives/backupid") {
        MockResponse().success()
      }
    )
  }
}
