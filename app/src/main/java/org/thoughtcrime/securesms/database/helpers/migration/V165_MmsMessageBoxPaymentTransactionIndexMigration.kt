package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * Adds an index to MMS table that only covers id and messages with the type of payment notification to
 * speed up look ups for payment messages.
 */
@Suppress("ClassName")
object V165_MmsMessageBoxPaymentTransactionIndexMigration : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("CREATE INDEX IF NOT EXISTS mms_id_msg_box_payment_transactions_index ON mms (_id, msg_box) WHERE msg_box & 0x300000000 != 0")
  }
}
