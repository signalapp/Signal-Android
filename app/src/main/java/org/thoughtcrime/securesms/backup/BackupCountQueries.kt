package org.thoughtcrime.securesms.backup

import org.thoughtcrime.securesms.database.AttachmentDatabase
import org.thoughtcrime.securesms.database.GroupReceiptDatabase
import org.thoughtcrime.securesms.database.MmsDatabase
import org.thoughtcrime.securesms.database.SmsDatabase

/**
 * Queries used by backup exporter to estimate total counts for various complicated tables.
 */
object BackupCountQueries {

  const val mmsCount: String = "SELECT COUNT(*) FROM ${MmsDatabase.TABLE_NAME} WHERE ${MmsDatabase.EXPIRES_IN} <= 0 AND ${MmsDatabase.VIEW_ONCE} <= 0"

  const val smsCount: String = "SELECT COUNT(*) FROM ${SmsDatabase.TABLE_NAME} WHERE ${SmsDatabase.EXPIRES_IN} <= 0"

  @get:JvmStatic
  val groupReceiptCount: String = """
      SELECT COUNT(*) FROM ${GroupReceiptDatabase.TABLE_NAME} 
      INNER JOIN ${MmsDatabase.TABLE_NAME} ON ${GroupReceiptDatabase.TABLE_NAME}.${GroupReceiptDatabase.MMS_ID} = ${MmsDatabase.TABLE_NAME}.${MmsDatabase.ID} 
      WHERE ${MmsDatabase.TABLE_NAME}.${MmsDatabase.EXPIRES_IN} <= 0 AND ${MmsDatabase.TABLE_NAME}.${MmsDatabase.VIEW_ONCE} <= 0
  """.trimIndent()

  @get:JvmStatic
  val attachmentCount: String = """
      SELECT COUNT(*) FROM ${AttachmentDatabase.TABLE_NAME} 
      INNER JOIN ${MmsDatabase.TABLE_NAME} ON ${AttachmentDatabase.TABLE_NAME}.${AttachmentDatabase.MMS_ID} = ${MmsDatabase.TABLE_NAME}.${MmsDatabase.ID} 
      WHERE ${MmsDatabase.TABLE_NAME}.${MmsDatabase.EXPIRES_IN} <= 0 AND ${MmsDatabase.TABLE_NAME}.${MmsDatabase.VIEW_ONCE} <= 0
  """.trimIndent()
}
