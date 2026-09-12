/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.push

import com.fasterxml.jackson.annotation.JsonProperty
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile
import java.math.BigDecimal

/**
 * Response JSON for a call to /v1/subscriptions/configuration
 */
data class SubscriptionsConfiguration(
  val currencies: Map<String, CurrencyConfiguration> = emptyMap(),
  val levels: Map<Int, LevelConfiguration> = emptyMap(),
  val sepaMaximumEuros: BigDecimal? = null,
  @JsonProperty("backup") val backupConfiguration: BackupConfiguration = BackupConfiguration()
) {

  data class CurrencyConfiguration(
    val minimum: BigDecimal = BigDecimal.ZERO,
    val oneTime: Map<Int, List<BigDecimal>> = emptyMap(),
    val subscription: Map<Int, BigDecimal> = emptyMap(),
    val backupSubscription: Map<Int, BigDecimal> = emptyMap(),
    val supportedPaymentMethods: Set<String> = emptySet()
  )

  data class LevelConfiguration(
    val badge: SignalServiceProfile.Badge? = null
  )

  data class BackupConfiguration(
    @JsonProperty("levels") val backupLevelConfigurationMap: Map<Int, BackupLevelConfiguration> = emptyMap(),
    val freeTierMediaDays: Int = 0
  )

  data class BackupLevelConfiguration(
    val storageAllowanceBytes: Long = 0,
    val playProductId: String = "",
    val mediaTtlDays: Long = 0
  )

  companion object {
    const val PAYPAL = "PAYPAL"
    const val CARD = "CARD"
    const val SEPA_DEBIT = "SEPA_DEBIT"
    const val IDEAL = "IDEAL"

    const val BOOST_LEVEL = 1
    const val GIFT_LEVEL = 100
    const val BACKUPS_LEVEL = 201

    @JvmField
    val SUBSCRIPTION_LEVELS = setOf(500, 1000, 2000)
  }
}
