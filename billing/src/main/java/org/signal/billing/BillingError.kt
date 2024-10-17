/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.billing

class BillingError(
  val billingResponseCode: Int
) : Exception()
