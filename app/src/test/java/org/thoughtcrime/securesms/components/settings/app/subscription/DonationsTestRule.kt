/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import org.junit.rules.ExternalResource
import org.signal.core.util.money.FiatMoney
import org.signal.donations.InAppPaymentType
import org.signal.donations.PaymentSourceType
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationSerializationHelper.toFiatValue
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository.toPaymentMethodType
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.util.RemoteConfig
import org.whispersystems.signalservice.internal.ServiceResponse
import org.whispersystems.signalservice.internal.push.SubscriptionsConfiguration
import org.whispersystems.signalservice.internal.util.JsonUtil
import java.math.BigDecimal
import java.util.Currency
import kotlin.time.Duration.Companion.milliseconds

/**
 * Common setup between different tests that rely on donations infrastructure.
 */
class DonationsTestRule : ExternalResource() {

  private val configuration: SubscriptionsConfiguration by lazy {
    val testConfigJsonData = javaClass.classLoader!!.getResourceAsStream("donations_configuration_test_data.json").bufferedReader().readText()

    JsonUtil.fromJson(testConfigJsonData, SubscriptionsConfiguration::class.java)
  }

  override fun before() {
    mockkStatic(RemoteConfig::class)
    every { RemoteConfig.init() } just runs

    mockkStatic(InAppPaymentsRepository::class)
    mockkObject(InAppPaymentsRepository)
    every { InAppPaymentsRepository.scheduleSyncForAccountRecordChange() } returns Unit

    mockkObject(InAppDonations)
    every { InAppDonations.isPayPalAvailable() } returns true
    every { InAppDonations.isGooglePayAvailable() } returns true
    every { InAppDonations.isSEPADebitAvailable() } returns true
    every { InAppDonations.isCreditCardAvailable() } returns true
    every { InAppDonations.isIDEALAvailable() } returns true

    mockkObject(SignalDatabase.Companion)
    every { SignalDatabase.Companion.inAppPayments } returns mockk {
      every { SignalDatabase.Companion.inAppPayments.update(any()) } returns Unit
    }
  }

  override fun after() {
    unmockkStatic(RemoteConfig::class, InAppPaymentsRepository::class)
    unmockkObject(InAppDonations, SignalDatabase.Companion)
  }

  /**
   * Because this initialisation requires reading from disk, we only want to do it in the exact tests that actually need it.
   */
  fun initializeDonationsConfigurationMock() {
    every { AppDependencies.donationsService.getDonationsConfiguration(any()) } returns ServiceResponse(200, "", configuration, null, null)
  }

  fun createInAppPayment(
    type: InAppPaymentType,
    paymentSourceType: PaymentSourceType
  ): InAppPaymentTable.InAppPayment {
    return InAppPaymentTable.InAppPayment(
      id = InAppPaymentTable.InAppPaymentId(1),
      state = InAppPaymentTable.State.CREATED,
      insertedAt = System.currentTimeMillis().milliseconds,
      updatedAt = System.currentTimeMillis().milliseconds,
      notified = true,
      subscriberId = null,
      endOfPeriod = 0.milliseconds,
      type = type,
      data = InAppPaymentData(
        badge = null,
        level = 500,
        paymentMethodType = paymentSourceType.toPaymentMethodType(),
        amount = FiatMoney(BigDecimal.ONE, Currency.getInstance("USD")).toFiatValue()
      )
    )
  }
}
