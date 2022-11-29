package org.thoughtcrime.securesms.database

import android.database.Cursor
import org.signal.core.util.CursorUtil
import org.signal.core.util.requireLong
import org.signal.spinner.ColumnTransformer
import org.signal.spinner.DefaultColumnTransformer
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.UpdateDescription
import org.thoughtcrime.securesms.database.model.databaseprotos.DecryptedGroupV2Context
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.util.Base64

object GV2UpdateTransformer : ColumnTransformer {
  override fun matches(tableName: String?, columnName: String): Boolean {
    return columnName == MmsSmsColumns.BODY && (tableName == null || (tableName == SmsTable.TABLE_NAME || tableName == MmsTable.TABLE_NAME))
  }

  override fun transform(tableName: String?, columnName: String, cursor: Cursor): String {
    val type: Long = cursor.getMessageType()

    if (type == -1L) {
      return DefaultColumnTransformer.transform(tableName, columnName, cursor)
    }

    val body: String? = CursorUtil.requireString(cursor, MmsSmsColumns.BODY)

    return if (MmsSmsColumns.Types.isGroupV2(type) && MmsSmsColumns.Types.isGroupUpdate(type) && body != null) {
      val decoded = Base64.decode(body)
      val decryptedGroupV2Context = DecryptedGroupV2Context.parseFrom(decoded)
      val gv2ChangeDescription: UpdateDescription = MessageRecord.getGv2ChangeDescription(ApplicationDependencies.getApplication(), body, null)

      "${gv2ChangeDescription.spannable}<br><br>${decryptedGroupV2Context.change}"
    } else {
      body ?: ""
    }
  }
}

private fun Cursor.getMessageType(): Long {
  return when {
    getColumnIndex(SmsTable.TYPE) != -1 -> requireLong(SmsTable.TYPE)
    getColumnIndex(MmsTable.MESSAGE_BOX) != -1 -> requireLong(MmsTable.MESSAGE_BOX)
    else -> -1
  }
}
