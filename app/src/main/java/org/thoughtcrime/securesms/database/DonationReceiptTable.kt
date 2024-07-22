package org.thoughtcrime.securesms.database

import android.content.Context
import android.database.Cursor
import androidx.core.content.contentValuesOf
import org.signal.core.util.CursorUtil
import org.signal.core.util.SqlUtil
import org.signal.core.util.money.FiatMoney
import org.thoughtcrime.securesms.database.model.InAppPaymentReceiptRecord
import java.math.BigDecimal
import java.util.Currency

class DonationReceiptTable(context: Context, databaseHelper: SignalDatabase) : DatabaseTable(context, databaseHelper) {
  companion object {
    private const val TABLE_NAME = "donation_receipt"

    private const val ID = "_id"
    private const val TYPE = "receipt_type"
    private const val DATE = "receipt_date"
    private const val AMOUNT = "amount"
    private const val CURRENCY = "currency"
    private const val SUBSCRIPTION_LEVEL = "subscription_level"

    @JvmField
    val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY AUTOINCREMENT,
        $TYPE TEXT NOT NULL,
        $DATE INTEGER NOT NULL,
        $AMOUNT TEXT NOT NULL,
        $CURRENCY TEXT NOT NULL,
        $SUBSCRIPTION_LEVEL INTEGER NOT NULL
      )
    """

    val CREATE_INDEXS = arrayOf(
      "CREATE INDEX IF NOT EXISTS donation_receipt_type_index ON $TABLE_NAME ($TYPE)",
      "CREATE INDEX IF NOT EXISTS donation_receipt_date_index ON $TABLE_NAME ($DATE)"
    )
  }

  fun hasReceipts(): Boolean {
    return readableDatabase.query(TABLE_NAME, SqlUtil.COUNT, null, null, null, null, null, null).use {
      it.moveToFirst()
      it.getInt(0) > 0
    }
  }

  fun addReceipt(record: InAppPaymentReceiptRecord) {
    require(record.id == -1L)

    val values = contentValuesOf(
      AMOUNT to record.amount.amount.toString(),
      CURRENCY to record.amount.currency.currencyCode,
      DATE to record.timestamp,
      TYPE to record.type.code,
      SUBSCRIPTION_LEVEL to record.subscriptionLevel
    )

    writableDatabase.insert(TABLE_NAME, null, values)
  }

  fun getReceipt(id: Long): InAppPaymentReceiptRecord? {
    readableDatabase.query(TABLE_NAME, null, ID_WHERE, SqlUtil.buildArgs(id), null, null, null).use { cursor ->
      return if (cursor.moveToNext()) {
        readRecord(cursor)
      } else {
        null
      }
    }
  }

  fun getReceipts(type: InAppPaymentReceiptRecord.Type?): List<InAppPaymentReceiptRecord> {
    val (where, whereArgs) = if (type != null) {
      "$TYPE = ?" to SqlUtil.buildArgs(type.code)
    } else {
      "$TYPE != ?" to SqlUtil.buildArgs(InAppPaymentReceiptRecord.Type.RECURRING_DONATION)
    }

    readableDatabase.query(TABLE_NAME, null, where, whereArgs, null, null, "$DATE DESC").use { cursor ->
      val results = ArrayList<InAppPaymentReceiptRecord>(cursor.count)
      while (cursor.moveToNext()) {
        results.add(readRecord(cursor))
      }

      return results
    }
  }

  private fun readRecord(cursor: Cursor): InAppPaymentReceiptRecord {
    return InAppPaymentReceiptRecord(
      id = CursorUtil.requireLong(cursor, ID),
      type = InAppPaymentReceiptRecord.Type.fromCode(CursorUtil.requireString(cursor, TYPE)),
      amount = FiatMoney(
        BigDecimal(CursorUtil.requireString(cursor, AMOUNT)),
        Currency.getInstance(CursorUtil.requireString(cursor, CURRENCY))
      ),
      timestamp = CursorUtil.requireLong(cursor, DATE),
      subscriptionLevel = CursorUtil.requireInt(cursor, SUBSCRIPTION_LEVEL)
    )
  }
}
