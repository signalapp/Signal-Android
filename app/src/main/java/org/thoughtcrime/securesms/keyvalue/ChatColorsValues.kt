package org.thoughtcrime.securesms.keyvalue

import org.thoughtcrime.securesms.conversation.colors.ChatColors
import org.thoughtcrime.securesms.database.model.databaseprotos.ChatColor
import java.io.IOException

class ChatColorsValues internal constructor(store: KeyValueStore) : SignalStoreValues(store) {

  companion object {
    private const val KEY_CHAT_COLORS = "chat_colors.chat_colors"
    private const val KEY_CHAT_COLORS_ID = "chat_colors.chat_colors.id"
    private const val KEY_CHAT_COLORS_AUTO_TOOLTIP = "chat_colors.auto.tooltip"
    private const val KEY_CHAT_COLORS_GRADIENT_TOOLTIP = "chat_colors.gradient.tooltip"
  }

  public override fun onFirstEverAppLaunch() = Unit

  public override fun getKeysToIncludeInBackup(): MutableList<String> = mutableListOf(
    KEY_CHAT_COLORS,
    KEY_CHAT_COLORS_ID,
    KEY_CHAT_COLORS_AUTO_TOOLTIP,
    KEY_CHAT_COLORS_GRADIENT_TOOLTIP
  )

  var shouldShowAutoTooltip: Boolean
    get() = getBoolean(KEY_CHAT_COLORS_AUTO_TOOLTIP, true)
    set(value) = putBoolean(KEY_CHAT_COLORS_AUTO_TOOLTIP, value)

  var shouldShowGradientTooltip: Boolean
    get() = getBoolean(KEY_CHAT_COLORS_GRADIENT_TOOLTIP, true)
    set(value) = putBoolean(KEY_CHAT_COLORS_GRADIENT_TOOLTIP, value)

  val hasChatColors: Boolean
    @JvmName("hasChatColors")
    get() = chatColors != null

  var chatColors: ChatColors?
    get() = getBlob(KEY_CHAT_COLORS, null)?.let { bytes ->
      try {
        ChatColors.forChatColor(chatColorsId, ChatColor.ADAPTER.decode(bytes))
      } catch (e: IOException) {
        null
      }
    }
    set(value) {
      if (value != null) {
        putBlob(KEY_CHAT_COLORS, value.serialize().encode())
        chatColorsId = value.id
      } else {
        remove(KEY_CHAT_COLORS)
      }
    }

  private var chatColorsId: ChatColors.Id
    get() = ChatColors.Id.forLongValue(getLong(KEY_CHAT_COLORS_ID, ChatColors.Id.NotSet.longValue))
    set(value) = putLong(KEY_CHAT_COLORS_ID, value.longValue)
}
