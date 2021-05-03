package org.thoughtcrime.securesms.keyvalue

import com.google.protobuf.InvalidProtocolBufferException
import org.thoughtcrime.securesms.conversation.colors.ChatColors
import org.thoughtcrime.securesms.database.model.databaseprotos.ChatColor

internal class ChatColorsValues internal constructor(store: KeyValueStore) : SignalStoreValues(store) {

  companion object {
    private const val KEY_CHAT_COLORS = "chat_colors.chat_colors"
    private const val KEY_CHAT_COLORS_ID = "chat_colors.chat_colors.id"
  }

  override fun onFirstEverAppLaunch() = Unit

  override fun getKeysToIncludeInBackup(): MutableList<String> = mutableListOf()

  val hasChatColors: Boolean
    @JvmName("hasChatColors")
    get() = chatColors != null

  var chatColors: ChatColors?
    get() = getBlob(KEY_CHAT_COLORS, null)?.let { bytes ->
      try {
        ChatColors.forChatColor(chatColorsId, ChatColor.parseFrom(bytes))
      } catch (e: InvalidProtocolBufferException) {
        null
      }
    }
    set(value) {
      if (value != null) {
        putBlob(KEY_CHAT_COLORS, value.serialize().toByteArray())
        chatColorsId = value.id
      } else {
        remove(KEY_CHAT_COLORS)
      }
    }

  private var chatColorsId: ChatColors.Id
    get() = ChatColors.Id.forLongValue(getLong(KEY_CHAT_COLORS_ID, ChatColors.Id.NotSet.longValue))
    set(value) = putLong(KEY_CHAT_COLORS_ID, value.longValue)
}
