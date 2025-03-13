/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import androidx.core.content.contentValuesOf
import org.signal.core.util.forEach
import org.signal.core.util.logging.Log
import org.signal.core.util.requireLong
import org.signal.core.util.requireNonNullBlob
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.SQLiteDatabase
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData

/**
 * Ensure consistent [InAppPaymentTable.State] and [InAppPaymentData.Error] state across the database.
 */
@Suppress("ClassName")
object V268_FixInAppPaymentsErrorStateConsistency : SignalDatabaseMigration {
  private const val KEEP_ALIVE = "keep-alive"
  private const val STATE_PENDING = 2L
  private const val STATE_END = 3L

  private val TAG = Log.tag(V268_FixInAppPaymentsErrorStateConsistency::class)

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.query("SELECT _id, state, data FROM in_app_payment").forEach {
      val id = it.requireLong("_id")
      val state = it.requireLong("state")
      val data = InAppPaymentData.ADAPTER.decode(it.requireNonNullBlob("data"))

      if (data.error?.data_ == KEEP_ALIVE && state != STATE_PENDING) {
        Log.d(TAG, "Detected a data inconsistency. Expected PENDING state but was State:$state")
        val newData = data.newBuilder().error(
          data.error.newBuilder().data_(null).build()
        ).build()

        updateInAppPayment(db, id, newData)
      } else if (data.error != null && state != STATE_END) {
        Log.d(TAG, "Detected a data inconsistency. Expected END state but was State:$state")
        updateInAppPayment(db, id, data)
      }
    }
  }

  private fun updateInAppPayment(db: SQLiteDatabase, id: Long, data: InAppPaymentData) {
    db.update(
      "in_app_payment",
      contentValuesOf(
        "state" to STATE_END,
        "data" to data.encode()
      ),
      "_id = ?",
      arrayOf(id.toString())
    )
  }
}
