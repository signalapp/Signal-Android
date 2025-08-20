package org.thoughtcrime.securesms.database

import android.database.Cursor
import org.signal.core.util.Base64
import org.signal.core.util.CursorUtil
import org.signal.core.util.requireLong
import org.signal.spinner.ColumnTransformer
import org.signal.spinner.DefaultColumnTransformer
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.UpdateDescription
import org.thoughtcrime.securesms.database.model.databaseprotos.DecryptedGroupV2Context
import org.thoughtcrime.securesms.database.model.databaseprotos.MessageExtras
import org.thoughtcrime.securesms.dependencies.AppDependencies

object GV2UpdateTransformer : ColumnTransformer {
  override fun matches(tableName: String?, columnName: String): Boolean {
    return (columnName == MessageTable.BODY || columnName == MessageTable.MESSAGE_EXTRAS) && (tableName == null || tableName == MessageTable.TABLE_NAME)
  }

  override fun transform(tableName: String?, columnName: String, cursor: Cursor): String? {
    val type: Long = cursor.getMessageType()

    if (type == -1L || !MessageTypes.isGroupV2(type) || !MessageTypes.isGroupUpdate(type)) {
      return DefaultColumnTransformer.transform(tableName, columnName, cursor)
    }

    return when (columnName) {
      MessageTable.BODY -> {
        val body: String? = CursorUtil.requireString(cursor, MessageTable.BODY)
        body?.let { bodyGroupUpdate(it) }
      }

      MessageTable.MESSAGE_EXTRAS -> {
        val messageExtras = CursorUtil.requireBlob(cursor, MessageTable.MESSAGE_EXTRAS)?.let { MessageExtras.ADAPTER.decode(it) }
        messageExtras?.let { messageExtrasGroupUpdate(messageExtras) }
      }

      else -> DefaultColumnTransformer.transform(tableName, columnName, cursor)
    }
  }

  private fun bodyGroupUpdate(body: String): String {
    val decoded = Base64.decode(body)
    val decryptedGroupV2Context = DecryptedGroupV2Context.ADAPTER.decode(decoded)
    val gv2ChangeDescription: UpdateDescription = MessageRecord.getGv2ChangeDescription(AppDependencies.application, body, null)

    return "${gv2ChangeDescription.spannable}<br><br>${decryptedGroupV2Context.change}"
  }

  private fun messageExtrasGroupUpdate(messageExtras: MessageExtras): String {
    val gv2ChangeDescription: UpdateDescription = MessageRecord.getGv2ChangeDescription(AppDependencies.application, messageExtras, null)

    val gv2ChangeProto: Any? = messageExtras.gv2UpdateDescription?.gv2ChangeDescription?.change ?: messageExtras.gv2UpdateDescription?.groupChangeUpdate

    return "${gv2ChangeDescription.spannable}<br><br>$gv2ChangeProto"
  }
}

private fun Cursor.getMessageType(): Long {
  return when {
    getColumnIndex(MessageTable.TYPE) != -1 -> requireLong(MessageTable.TYPE)
    else -> -1
  }
}
