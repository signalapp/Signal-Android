package org.thoughtcrime.securesms.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import org.signal.core.util.CursorUtil
import org.signal.core.util.SqlUtil
import org.thoughtcrime.securesms.conversation.colors.ChatColors
import org.thoughtcrime.securesms.database.model.databaseprotos.ChatColor
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore

class ChatColorsTable(context: Context, databaseHelper: SignalDatabase) : DatabaseTable(context, databaseHelper) {

  companion object {
    const val TABLE_NAME = "chat_colors"
    private const val ID = "_id"
    private const val CHAT_COLORS = "chat_colors"

    @JvmField
    val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY AUTOINCREMENT,
        $CHAT_COLORS BLOB
      )
    """
  }

  fun getById(chatColorsId: ChatColors.Id): ChatColors {
    val db = databaseHelper.signalReadableDatabase
    val projection = arrayOf(ID, CHAT_COLORS)
    val args = SqlUtil.buildArgs(chatColorsId.longValue)

    db.query(TABLE_NAME, projection, ID_WHERE, args, null, null, null)?.use {
      if (it.moveToFirst()) {
        return it.getChatColors()
      }
    }

    throw IllegalArgumentException("Could not locate chat color $chatColorsId")
  }

  fun saveChatColors(chatColors: ChatColors): ChatColors {
    return when (chatColors.id) {
      is ChatColors.Id.Auto -> throw AssertionError("Saving 'auto' does not make sense")
      is ChatColors.Id.BuiltIn -> chatColors
      is ChatColors.Id.NotSet -> insertChatColors(chatColors)
      is ChatColors.Id.Custom -> updateChatColors(chatColors)
    }
  }

  fun getSavedChatColors(): List<ChatColors> {
    val db = databaseHelper.signalReadableDatabase
    val projection = arrayOf(ID, CHAT_COLORS)
    val result = mutableListOf<ChatColors>()

    db.query(TABLE_NAME, projection, null, null, null, null, null)?.use {
      while (it.moveToNext()) {
        result.add(it.getChatColors())
      }
    }

    return result
  }

  private fun insertChatColors(chatColors: ChatColors): ChatColors {
    if (chatColors.id != ChatColors.Id.NotSet) {
      throw IllegalArgumentException("Bad chat colors to insert.")
    }

    val db: SQLiteDatabase = databaseHelper.signalWritableDatabase
    val values = ContentValues(1).apply {
      put(CHAT_COLORS, chatColors.serialize().encode())
    }

    val rowId = db.insert(TABLE_NAME, null, values)
    if (rowId == -1L) {
      throw IllegalStateException("Failed to insert ChatColor into database")
    }

    notifyListeners()

    return chatColors.withId(ChatColors.Id.forLongValue(rowId))
  }

  private fun updateChatColors(chatColors: ChatColors): ChatColors {
    if (chatColors.id == ChatColors.Id.NotSet || chatColors.id == ChatColors.Id.BuiltIn) {
      throw IllegalArgumentException("Bad chat colors to update.")
    }

    val db: SQLiteDatabase = databaseHelper.signalWritableDatabase
    val values = ContentValues(1).apply {
      put(CHAT_COLORS, chatColors.serialize().encode())
    }

    val rowsUpdated = db.update(TABLE_NAME, values, ID_WHERE, SqlUtil.buildArgs(chatColors.id.longValue))
    if (rowsUpdated < 1) {
      throw IllegalStateException("Failed to update ChatColor in database")
    }

    if (SignalStore.chatColors.chatColors?.id == chatColors.id) {
      SignalStore.chatColors.chatColors = chatColors
    }

    SignalDatabase.recipients.onUpdatedChatColors(chatColors)
    notifyListeners()

    return chatColors
  }

  fun deleteChatColors(chatColors: ChatColors) {
    if (chatColors.id == ChatColors.Id.NotSet || chatColors.id == ChatColors.Id.BuiltIn) {
      throw IllegalArgumentException("Cannot delete this chat color")
    }

    val db: SQLiteDatabase = databaseHelper.signalWritableDatabase
    db.delete(TABLE_NAME, ID_WHERE, SqlUtil.buildArgs(chatColors.id.longValue))

    if (SignalStore.chatColors.chatColors?.id == chatColors.id) {
      SignalStore.chatColors.chatColors = null
    }

    SignalDatabase.recipients.onDeletedChatColors(chatColors)
    notifyListeners()
  }

  private fun notifyListeners() {
    AppDependencies.databaseObserver.notifyChatColorsListeners()
  }

  private fun Cursor.getId(): Long = CursorUtil.requireLong(this, ID)
  private fun Cursor.getChatColors(): ChatColors = ChatColors.forChatColor(
    ChatColors.Id.forLongValue(getId()),
    ChatColor.ADAPTER.decode(CursorUtil.requireBlob(this, CHAT_COLORS))
  )
}
