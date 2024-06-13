/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.model

import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.whispersystems.signalservice.api.subscriptions.SubscriberId
import java.util.Currency

/**
 * Represents a SubscriberId and metadata that can be used for a recurring
 * subscription of the given type. Stored in InAppPaymentSubscriberTable
 */
data class InAppPaymentSubscriberRecord(
  val subscriberId: SubscriberId,
  val currency: Currency,
  val type: Type,
  val requiresCancel: Boolean,
  val paymentMethodType: InAppPaymentData.PaymentMethodType
) {
  /**
   * Serves as the mutex by which to perform mutations to subscriptions.
   */
  enum class Type(val code: Int, val jobQueue: String, val inAppPaymentType: InAppPaymentTable.Type) {
    /**
     * A recurring donation
     */
    DONATION(0, "recurring-donations", InAppPaymentTable.Type.RECURRING_DONATION),

    /**
     * A recurring backups subscription
     */
    BACKUP(1, "recurring-backups", InAppPaymentTable.Type.RECURRING_BACKUP)
  }
}
