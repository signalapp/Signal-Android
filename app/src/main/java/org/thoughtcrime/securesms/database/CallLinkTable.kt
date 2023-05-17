package org.thoughtcrime.securesms.database

import android.content.Context
import androidx.core.content.contentValuesOf
import org.signal.core.util.logging.Log
import org.signal.core.util.update
import org.thoughtcrime.securesms.conversation.colors.AvatarColor
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Table containing ad-hoc call link details
 */
class CallLinkTable(context: Context, databaseHelper: SignalDatabase) : DatabaseTable(context, databaseHelper), RecipientIdDatabaseReference {

  companion object {
    private val TAG = Log.tag(CallLinkTable::class.java)

    const val TABLE_NAME = "call_link"
    const val ID = "_id"
    const val ROOT_KEY = "root_key"
    const val ROOM_ID = "room_id"
    const val ADMIN_KEY = "admin_key"
    const val NAME = "name"
    const val RESTRICTIONS = "restrictions"
    const val REVOKED = "revoked"
    const val EXPIRATION = "expiration"
    const val AVATAR_COLOR = "avatar_color"
    const val RECIPIENT_ID = "recipient_id"

    //language=sql
    const val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY,
        $ROOT_KEY BLOB,
        $ROOM_ID TEXT NOT NULL UNIQUE,
        $ADMIN_KEY BLOB,
        $NAME TEXT NOT NULL,
        $RESTRICTIONS INTEGER NOT NULL,
        $REVOKED INTEGER NOT NULL,
        $EXPIRATION INTEGER NOT NULL,
        $AVATAR_COLOR TEXT NOT NULL,
        $RECIPIENT_ID INTEGER UNIQUE REFERENCES ${RecipientTable.TABLE_NAME} (${RecipientTable.ID}) ON DELETE CASCADE
      )
    """
  }

  data class CallLink(
    val name: String,
    val identifier: String,
    val avatarColor: AvatarColor,
    val isApprovalRequired: Boolean
  )

  override fun remapRecipient(fromId: RecipientId, toId: RecipientId) {
    writableDatabase.update(TABLE_NAME)
      .values(
        contentValuesOf(
          RECIPIENT_ID to toId.toLong()
        )
      )
      .where("$RECIPIENT_ID = ?", fromId.toLong())
      .run()
  }
}
