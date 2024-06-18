/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import androidx.core.content.contentValuesOf
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.signal.core.util.logging.Log
import org.signal.core.util.update
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData

/**
 * Fixes a bug in storage sync where we could end up overwriting a subscriber's currency code with
 * an empty string. This can't happen anymore, as we now require a Currency on the InAppPaymentSubscriberRecord
 * instead of just a currency code string.
 *
 * If a subscriber has a null or empty currency code, we try to load the code from the
 * in app payments table. We utilize CONFLICT_IGNORE because if there's already a new subscriber id
 * created, we don't want to impact it.
 *
 * Because the data column is a protobuf encoded blob, we cannot do a raw query here.
 */
@Suppress("ClassName")
object V236_FixInAppSubscriberCurrencyIfAble : SignalDatabaseMigration {

  private val TAG = Log.tag(V236_FixInAppSubscriberCurrencyIfAble::class)

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    val subscriberIds: List<String> = db.query(
      "in_app_payment_subscriber",
      arrayOf("subscriber_id"),
      "currency_code = ?",
      arrayOf(""),
      null,
      null,
      "_id DESC"
    ).use { cursor ->
      val ids = mutableListOf<String>()
      val columnIndex = cursor.getColumnIndexOrThrow("subscriber_id")
      while (cursor.moveToNext()) {
        ids.add(cursor.getString(columnIndex))
      }

      ids
    }

    for (id in subscriberIds) {
      val currencyCode: String? = db.query(
        "in_app_payment",
        arrayOf("data"),
        "subscriber_id = ?",
        arrayOf(id),
        null,
        null,
        "inserted_at DESC",
        "1"
      ).use { cursor ->
        if (cursor.moveToFirst()) {
          val columnIndex = cursor.getColumnIndexOrThrow("data")
          val rawData = cursor.getBlob(columnIndex)
          val data = InAppPaymentData.ADAPTER.decode(rawData)

          data.amount?.currencyCode
        } else {
          null
        }
      }

      if (currencyCode != null) {
        Log.d(TAG, "Found and attempting to heal subscriber of currency $currencyCode")
        db.update(
          "in_app_payment_subscriber",
          SQLiteDatabase.CONFLICT_IGNORE,
          contentValuesOf("currency_code" to currencyCode),
          "subscriber_id = ?",
          arrayOf(id)
        )
      }
    }
  }
}
