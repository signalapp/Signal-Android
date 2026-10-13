/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.payments

data class CurrencyConversion(
  val base: String = "",
  val conversions: Map<String, Double> = emptyMap()
)
