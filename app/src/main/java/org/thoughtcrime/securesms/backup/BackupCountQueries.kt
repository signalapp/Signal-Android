package org.thoughtcrime.securesms.backup

import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.GroupReceiptTable
import org.thoughtcrime.securesms.database.MmsTable
import org.thoughtcrime.securesms.database.SmsTable

/**
 * Queries used by backup exporter to estimate total counts for various complicated tables.
 */
object BackupCountQueries {

  const val mmsCount: String = "SELECT COUNT(*) FROM ${MmsTable.TABLE_NAME} WHERE ${MmsTable.EXPIRES_IN} <= 0 AND ${MmsTable.VIEW_ONCE} <= 0"

  const val smsCount: String = "SELECT COUNT(*) FROM ${SmsTable.TABLE_NAME} WHERE ${SmsTable.EXPIRES_IN} <= 0"

  @get:JvmStatic
  val groupReceiptCount: String = """
      SELECT COUNT(*) FROM ${GroupReceiptTable.TABLE_NAME} 
      INNER JOIN ${MmsTable.TABLE_NAME} ON ${GroupReceiptTable.TABLE_NAME}.${GroupReceiptTable.MMS_ID} = ${MmsTable.TABLE_NAME}.${MmsTable.ID} 
      WHERE ${MmsTable.TABLE_NAME}.${MmsTable.EXPIRES_IN} <= 0 AND ${MmsTable.TABLE_NAME}.${MmsTable.VIEW_ONCE} <= 0
  """.trimIndent()

  @get:JvmStatic
  val attachmentCount: String = """
      SELECT COUNT(*) FROM ${AttachmentTable.TABLE_NAME} 
      INNER JOIN ${MmsTable.TABLE_NAME} ON ${AttachmentTable.TABLE_NAME}.${AttachmentTable.MMS_ID} = ${MmsTable.TABLE_NAME}.${MmsTable.ID} 
      WHERE ${MmsTable.TABLE_NAME}.${MmsTable.EXPIRES_IN} <= 0 AND ${MmsTable.TABLE_NAME}.${MmsTable.VIEW_ONCE} <= 0
  """.trimIndent()
}
