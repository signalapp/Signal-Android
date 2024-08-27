/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import androidx.annotation.VisibleForTesting
import androidx.core.content.contentValuesOf
import org.signal.core.util.DatabaseSerializer
import org.signal.core.util.Serializer
import org.signal.core.util.insertInto
import org.signal.core.util.logging.Log
import org.signal.core.util.readToSingleObject
import org.signal.core.util.requireBoolean
import org.signal.core.util.requireInt
import org.signal.core.util.requireNonNullString
import org.signal.core.util.select
import org.signal.core.util.update
import org.signal.core.util.withinTransaction
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.whispersystems.signalservice.api.subscriptions.SubscriberId
import java.util.Currency

/**
 * A table matching up SubscriptionIds to currency codes and type
 */
class InAppPaymentSubscriberTable(
  context: Context,
  databaseHelper: SignalDatabase
) : DatabaseTable(context, databaseHelper) {

  companion object {
    private val TAG = Log.tag(InAppPaymentSubscriberRecord::class.java)

    @VisibleForTesting
    const val TABLE_NAME = "in_app_payment_subscriber"

    /** Row ID */
    private const val ID = "_id"

    /** The serialized subscriber id */
    @VisibleForTesting
    const val SUBSCRIBER_ID = "subscriber_id"

    /** The currency code for this subscriber id */
    @VisibleForTesting
    const val CURRENCY_CODE = "currency_code"

    /** The type of subscription used by this subscriber id */
    private const val TYPE = "type"

    /** Specifies whether we should try to cancel any current subscription before starting a new one with this ID */
    private const val REQUIRES_CANCEL = "requires_cancel"

    /** Specifies which payment method was utilized for the latest transaction with this id */
    private const val PAYMENT_METHOD_TYPE = "payment_method_type"

    const val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY,
        $SUBSCRIBER_ID TEXT NOT NULL UNIQUE,
        $CURRENCY_CODE TEXT NOT NULL,
        $TYPE INTEGER NOT NULL,
        $REQUIRES_CANCEL INTEGER DEFAULT 0,
        $PAYMENT_METHOD_TYPE INTEGER DEFAULT 0,
        UNIQUE($CURRENCY_CODE, $TYPE)
      )
    """
  }

  /**
   * Inserts this subscriber, replacing any that it conflicts with.
   *
   * This is a destructive, mutating operation. For setting specific values, prefer the alternative setters available on this table class.
   */
  fun insertOrReplace(inAppPaymentSubscriberRecord: InAppPaymentSubscriberRecord) {
    Log.i(TAG, "Setting subscriber for currency ${inAppPaymentSubscriberRecord.currency.currencyCode}", Exception(), true)

    writableDatabase.withinTransaction { db ->
      db.insertInto(TABLE_NAME)
        .values(InAppPaymentSubscriberSerializer.serialize(inAppPaymentSubscriberRecord))
        .run(conflictStrategy = SQLiteDatabase.CONFLICT_REPLACE)

      SignalStore.inAppPayments.setSubscriberCurrency(
        inAppPaymentSubscriberRecord.currency,
        inAppPaymentSubscriberRecord.type
      )
    }
  }

  /**
   * Sets whether the subscriber in question requires a cancellation before a new subscription can be created.
   */
  fun setRequiresCancel(subscriberId: SubscriberId, requiresCancel: Boolean) {
    writableDatabase.update(TABLE_NAME)
      .values(REQUIRES_CANCEL to requiresCancel)
      .where("$SUBSCRIBER_ID = ?", subscriberId.serialize())
      .run()
  }

  /**
   * Updates the payment method on a subscriber.
   */
  fun setPaymentMethod(subscriberId: SubscriberId, paymentMethodType: InAppPaymentData.PaymentMethodType) {
    writableDatabase.update(TABLE_NAME)
      .values(PAYMENT_METHOD_TYPE to paymentMethodType.value)
      .where("$SUBSCRIBER_ID = ?", subscriberId.serialize())
      .run()
  }

  /**
   * Retrieves a subscriber for the given type by the currency code.
   */
  fun getByCurrencyCode(currencyCode: String, type: InAppPaymentSubscriberRecord.Type): InAppPaymentSubscriberRecord? {
    return readableDatabase.select()
      .from(TABLE_NAME)
      .where("$TYPE = ? AND $CURRENCY_CODE = ?", TypeSerializer.serialize(type), currencyCode.uppercase())
      .run()
      .readToSingleObject(InAppPaymentSubscriberSerializer)
  }

  /**
   * Retrieves a subscriber by SubscriberId
   */
  fun getBySubscriberId(subscriberId: SubscriberId): InAppPaymentSubscriberRecord? {
    return readableDatabase.select()
      .from(TABLE_NAME)
      .where("$SUBSCRIBER_ID = ?", subscriberId.serialize())
      .run()
      .readToSingleObject(InAppPaymentSubscriberSerializer)
  }

  object InAppPaymentSubscriberSerializer : DatabaseSerializer<InAppPaymentSubscriberRecord> {
    override fun serialize(data: InAppPaymentSubscriberRecord): ContentValues {
      return contentValuesOf(
        SUBSCRIBER_ID to data.subscriberId.serialize(),
        CURRENCY_CODE to data.currency.currencyCode.uppercase(),
        TYPE to TypeSerializer.serialize(data.type),
        REQUIRES_CANCEL to data.requiresCancel,
        PAYMENT_METHOD_TYPE to data.paymentMethodType.value
      )
    }

    override fun deserialize(input: Cursor): InAppPaymentSubscriberRecord {
      val type = TypeSerializer.deserialize(input.requireInt(TYPE))
      val currencyCode = input.requireNonNullString(CURRENCY_CODE).takeIf { it.isNotEmpty() }
      return InAppPaymentSubscriberRecord(
        subscriberId = SubscriberId.deserialize(input.requireNonNullString(SUBSCRIBER_ID)),
        currency = currencyCode?.let { Currency.getInstance(it) } ?: SignalStore.inAppPayments.getSubscriptionCurrency(type),
        type = type,
        requiresCancel = input.requireBoolean(REQUIRES_CANCEL) || currencyCode.isNullOrBlank(),
        paymentMethodType = InAppPaymentData.PaymentMethodType.fromValue(input.requireInt(PAYMENT_METHOD_TYPE)) ?: InAppPaymentData.PaymentMethodType.UNKNOWN
      )
    }
  }

  object TypeSerializer : Serializer<InAppPaymentSubscriberRecord.Type, Int> {
    override fun serialize(data: InAppPaymentSubscriberRecord.Type): Int = data.code
    override fun deserialize(input: Int): InAppPaymentSubscriberRecord.Type = InAppPaymentSubscriberRecord.Type.values().first { it.code == input }
  }
}
