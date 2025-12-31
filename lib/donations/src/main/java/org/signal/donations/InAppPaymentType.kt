/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.donations

import org.signal.core.util.Serializer

enum class InAppPaymentType(val code: Int, val recurring: Boolean) {
  /**
   * Used explicitly for mapping DonationErrorSource. Writing this value
   * into an InAppPayment is an error.
   */
  UNKNOWN(-1, false),

  /**
   * This payment is for a gift badge
   */
  ONE_TIME_GIFT(0, false),

  /**
   * This payment is for a one-time donation
   */
  ONE_TIME_DONATION(1, false),

  /**
   * This payment is for a recurring donation
   */
  RECURRING_DONATION(2, true),

  /**
   * This payment is for a recurring backup payment
   */
  RECURRING_BACKUP(3, true);

  companion object : Serializer<InAppPaymentType, Int> {
    override fun serialize(data: InAppPaymentType): Int = data.code
    override fun deserialize(input: Int): InAppPaymentType = entries.first { it.code == input }
  }
}
