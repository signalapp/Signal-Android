/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.exporters

import android.database.Cursor
import org.signal.core.util.decodeOrNull
import org.signal.core.util.requireBlob
import org.signal.core.util.requireBoolean
import org.signal.core.util.requireInt
import org.signal.core.util.requireIntOrNull
import org.signal.core.util.requireLong
import org.thoughtcrime.securesms.backup.v2.proto.Chat
import org.thoughtcrime.securesms.backup.v2.util.ChatStyleConverter
import org.thoughtcrime.securesms.conversation.colors.ChatColors
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.ThreadTable
import org.thoughtcrime.securesms.database.model.databaseprotos.ChatColor
import org.thoughtcrime.securesms.database.model.databaseprotos.Wallpaper
import java.io.Closeable
import kotlin.time.Duration.Companion.seconds

class ChatArchiveExporter(private val cursor: Cursor, private val db: SignalDatabase, private val includeImageWallpapers: Boolean) : Iterator<Chat>, Closeable {
  override fun hasNext(): Boolean {
    return cursor.count > 0 && !cursor.isLast
  }

  override fun next(): Chat {
    if (!cursor.moveToNext()) {
      throw NoSuchElementException()
    }

    val customChatColorsId = ChatColors.Id.forLongValue(cursor.requireLong(RecipientTable.CUSTOM_CHAT_COLORS_ID))

    val chatColors: ChatColors? = cursor.requireBlob(RecipientTable.CHAT_COLORS)?.let { serializedChatColors ->
      val chatColor = ChatColor.ADAPTER.decodeOrNull(serializedChatColors)
      chatColor?.let { ChatColors.forChatColor(customChatColorsId, it) }
    }

    val chatWallpaper: Wallpaper? = cursor.requireBlob(RecipientTable.WALLPAPER)?.let { serializedWallpaper ->
      val wallpaper = Wallpaper.ADAPTER.decodeOrNull(serializedWallpaper)
      val isImageWallpaper = wallpaper?.file_ != null

      if (includeImageWallpapers || !isImageWallpaper) {
        wallpaper
      } else {
        null
      }
    }

    return Chat(
      id = cursor.requireLong(ThreadTable.ID),
      recipientId = cursor.requireLong(ThreadTable.RECIPIENT_ID),
      archived = cursor.requireBoolean(ThreadTable.ARCHIVED),
      pinnedOrder = cursor.requireIntOrNull(ThreadTable.PINNED_ORDER),
      expirationTimerMs = cursor.requireLong(RecipientTable.MESSAGE_EXPIRATION_TIME).seconds.inWholeMilliseconds.takeIf { it > 0 },
      expireTimerVersion = cursor.requireInt(RecipientTable.MESSAGE_EXPIRATION_TIME_VERSION),
      muteUntilMs = cursor.requireLong(RecipientTable.MUTE_UNTIL).takeIf { it > 0 },
      markedUnread = ThreadTable.ReadStatus.deserialize(cursor.requireInt(ThreadTable.READ)) == ThreadTable.ReadStatus.FORCED_UNREAD,
      dontNotifyForMentionsIfMuted = RecipientTable.MentionSetting.DO_NOT_NOTIFY.id == cursor.requireInt(RecipientTable.MENTION_SETTING),
      style = ChatStyleConverter.constructRemoteChatStyle(
        db = db,
        chatColors = chatColors,
        chatColorId = customChatColorsId,
        chatWallpaper = chatWallpaper
      )
    )
  }

  override fun close() {
    cursor.close()
  }
}
