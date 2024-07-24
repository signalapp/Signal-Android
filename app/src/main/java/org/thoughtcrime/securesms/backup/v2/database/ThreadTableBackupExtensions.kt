/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.database

import android.database.Cursor
import androidx.core.content.contentValuesOf
import com.google.protobuf.InvalidProtocolBufferException
import org.signal.core.util.SqlUtil
import org.signal.core.util.insertInto
import org.signal.core.util.logging.Log
import org.signal.core.util.requireBlob
import org.signal.core.util.requireBoolean
import org.signal.core.util.requireInt
import org.signal.core.util.requireLong
import org.signal.core.util.toInt
import org.thoughtcrime.securesms.backup.v2.proto.Chat
import org.thoughtcrime.securesms.backup.v2.proto.ChatStyle
import org.thoughtcrime.securesms.conversation.colors.ChatColors
import org.thoughtcrime.securesms.conversation.colors.ChatColorsPalette
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.ThreadTable
import org.thoughtcrime.securesms.database.model.databaseprotos.ChatColor
import org.thoughtcrime.securesms.recipients.RecipientId
import java.io.Closeable

private val TAG = Log.tag(ThreadTable::class.java)

fun ThreadTable.getThreadsForBackup(): ChatIterator {
  //language=sql
  val query = """
      SELECT 
        ${ThreadTable.TABLE_NAME}.${ThreadTable.ID}, 
        ${ThreadTable.RECIPIENT_ID}, 
        ${ThreadTable.PINNED}, 
        ${ThreadTable.READ}, 
        ${ThreadTable.ARCHIVED},
        ${RecipientTable.TABLE_NAME}.${RecipientTable.MESSAGE_EXPIRATION_TIME},
        ${RecipientTable.TABLE_NAME}.${RecipientTable.MUTE_UNTIL},
        ${RecipientTable.TABLE_NAME}.${RecipientTable.MENTION_SETTING}, 
        ${RecipientTable.TABLE_NAME}.${RecipientTable.CHAT_COLORS},
        ${RecipientTable.TABLE_NAME}.${RecipientTable.CUSTOM_CHAT_COLORS_ID}
      FROM ${ThreadTable.TABLE_NAME} 
        LEFT OUTER JOIN ${RecipientTable.TABLE_NAME} ON ${ThreadTable.TABLE_NAME}.${ThreadTable.RECIPIENT_ID} = ${RecipientTable.TABLE_NAME}.${RecipientTable.ID}
      WHERE ${ThreadTable.ACTIVE} = 1
    """
  val cursor = readableDatabase.query(query)

  return ChatIterator(cursor)
}

fun ThreadTable.clearAllDataForBackupRestore() {
  writableDatabase.delete(ThreadTable.TABLE_NAME, null, null)
  SqlUtil.resetAutoIncrementValue(writableDatabase, ThreadTable.TABLE_NAME)
  clearCache()
}

fun ThreadTable.restoreFromBackup(chat: Chat, recipientId: RecipientId): Long? {
  val chatColor = chat.style?.parseChatColor()
  val chatColorWithId = if (chatColor != null && chatColor.id is ChatColors.Id.NotSet) {
    val savedColors = SignalDatabase.chatColors.getSavedChatColors()
    val match = savedColors.find { it.matchesWithoutId(chatColor) }
    match ?: SignalDatabase.chatColors.saveChatColors(chatColor)
  } else {
    chatColor
  }

  val threadId = writableDatabase
    .insertInto(ThreadTable.TABLE_NAME)
    .values(
      ThreadTable.RECIPIENT_ID to recipientId.serialize(),
      ThreadTable.PINNED to chat.pinnedOrder,
      ThreadTable.ARCHIVED to chat.archived.toInt(),
      ThreadTable.READ to if (chat.markedUnread) ThreadTable.ReadStatus.FORCED_UNREAD.serialize() else ThreadTable.ReadStatus.READ.serialize(),
      ThreadTable.ACTIVE to 1
    )
    .run()
  writableDatabase
    .update(
      RecipientTable.TABLE_NAME,
      contentValuesOf(
        RecipientTable.MENTION_SETTING to (if (chat.dontNotifyForMentionsIfMuted) RecipientTable.MentionSetting.DO_NOT_NOTIFY.id else RecipientTable.MentionSetting.ALWAYS_NOTIFY.id),
        RecipientTable.MUTE_UNTIL to chat.muteUntilMs,
        RecipientTable.MESSAGE_EXPIRATION_TIME to chat.expirationTimerMs,
        RecipientTable.CHAT_COLORS to chatColorWithId?.serialize()?.encode(),
        RecipientTable.CUSTOM_CHAT_COLORS_ID to (chatColorWithId?.id ?: ChatColors.Id.NotSet).longValue
      ),
      "${RecipientTable.ID} = ?",
      SqlUtil.buildArgs(recipientId.toLong())
    )

  return threadId
}

class ChatIterator(private val cursor: Cursor) : Iterator<Chat>, Closeable {
  override fun hasNext(): Boolean {
    return cursor.count > 0 && !cursor.isLast
  }

  override fun next(): Chat {
    if (!cursor.moveToNext()) {
      throw NoSuchElementException()
    }

    val serializedChatColors = cursor.requireBlob(RecipientTable.CHAT_COLORS)
    val customChatColorsId = ChatColors.Id.forLongValue(cursor.requireLong(RecipientTable.CUSTOM_CHAT_COLORS_ID))
    val chatColors: ChatColors? = if (serializedChatColors != null) {
      try {
        ChatColors.forChatColor(customChatColorsId, ChatColor.ADAPTER.decode(serializedChatColors))
      } catch (e: InvalidProtocolBufferException) {
        null
      }
    } else {
      null
    }

    var chatStyleBuilder: ChatStyle.Builder? = null
    if (chatColors != null) {
      chatStyleBuilder = ChatStyle.Builder()
      val presetBubbleColor = chatColors.tryToMapToBackupPreset()
      if (presetBubbleColor != null) {
        chatStyleBuilder.bubbleColorPreset = presetBubbleColor
      } else if (chatColors.isGradient()) {
        chatStyleBuilder.bubbleGradient = ChatStyle.Gradient(angle = chatColors.getDegrees().toInt(), colors = chatColors.getColors().toList())
      } else if (customChatColorsId is ChatColors.Id.Auto) {
        chatStyleBuilder.autoBubbleColor = ChatStyle.AutomaticBubbleColor()
      } else {
        chatStyleBuilder.bubbleSolidColor = chatColors.asSingleColor()
      }
    }

    return Chat(
      id = cursor.requireLong(ThreadTable.ID),
      recipientId = cursor.requireLong(ThreadTable.RECIPIENT_ID),
      archived = cursor.requireBoolean(ThreadTable.ARCHIVED),
      pinnedOrder = cursor.requireInt(ThreadTable.PINNED),
      expirationTimerMs = cursor.requireLong(RecipientTable.MESSAGE_EXPIRATION_TIME),
      muteUntilMs = cursor.requireLong(RecipientTable.MUTE_UNTIL),
      markedUnread = ThreadTable.ReadStatus.deserialize(cursor.requireInt(ThreadTable.READ)) == ThreadTable.ReadStatus.FORCED_UNREAD,
      dontNotifyForMentionsIfMuted = RecipientTable.MentionSetting.DO_NOT_NOTIFY.id == cursor.requireInt(RecipientTable.MENTION_SETTING),
      style = chatStyleBuilder?.build()
    )
  }

  override fun close() {
    cursor.close()
  }
}

private fun ChatStyle.parseChatColor(): ChatColors? {
  if (bubbleColorPreset != null) {
    return when (bubbleColorPreset) {
      ChatStyle.BubbleColorPreset.SOLID_CRIMSON -> ChatColorsPalette.Bubbles.CRIMSON
      ChatStyle.BubbleColorPreset.SOLID_VERMILION -> ChatColorsPalette.Bubbles.VERMILION
      ChatStyle.BubbleColorPreset.SOLID_BURLAP -> ChatColorsPalette.Bubbles.BURLAP
      ChatStyle.BubbleColorPreset.SOLID_FOREST -> ChatColorsPalette.Bubbles.FOREST
      ChatStyle.BubbleColorPreset.SOLID_WINTERGREEN -> ChatColorsPalette.Bubbles.WINTERGREEN
      ChatStyle.BubbleColorPreset.SOLID_TEAL -> ChatColorsPalette.Bubbles.TEAL
      ChatStyle.BubbleColorPreset.SOLID_BLUE -> ChatColorsPalette.Bubbles.BLUE
      ChatStyle.BubbleColorPreset.SOLID_INDIGO -> ChatColorsPalette.Bubbles.INDIGO
      ChatStyle.BubbleColorPreset.SOLID_VIOLET -> ChatColorsPalette.Bubbles.VIOLET
      ChatStyle.BubbleColorPreset.SOLID_PLUM -> ChatColorsPalette.Bubbles.PLUM
      ChatStyle.BubbleColorPreset.SOLID_TAUPE -> ChatColorsPalette.Bubbles.TAUPE
      ChatStyle.BubbleColorPreset.SOLID_STEEL -> ChatColorsPalette.Bubbles.STEEL
      ChatStyle.BubbleColorPreset.GRADIENT_EMBER -> ChatColorsPalette.Bubbles.EMBER
      ChatStyle.BubbleColorPreset.GRADIENT_MIDNIGHT -> ChatColorsPalette.Bubbles.MIDNIGHT
      ChatStyle.BubbleColorPreset.GRADIENT_INFRARED -> ChatColorsPalette.Bubbles.INFRARED
      ChatStyle.BubbleColorPreset.GRADIENT_LAGOON -> ChatColorsPalette.Bubbles.LAGOON
      ChatStyle.BubbleColorPreset.GRADIENT_FLUORESCENT -> ChatColorsPalette.Bubbles.FLUORESCENT
      ChatStyle.BubbleColorPreset.GRADIENT_BASIL -> ChatColorsPalette.Bubbles.BASIL
      ChatStyle.BubbleColorPreset.GRADIENT_SUBLIME -> ChatColorsPalette.Bubbles.SUBLIME
      ChatStyle.BubbleColorPreset.GRADIENT_SEA -> ChatColorsPalette.Bubbles.SEA
      ChatStyle.BubbleColorPreset.GRADIENT_TANGERINE -> ChatColorsPalette.Bubbles.TANGERINE
      ChatStyle.BubbleColorPreset.UNKNOWN_BUBBLE_COLOR_PRESET, ChatStyle.BubbleColorPreset.SOLID_ULTRAMARINE -> ChatColorsPalette.Bubbles.ULTRAMARINE
    }
  }
  if (autoBubbleColor != null) {
    return ChatColorsPalette.Bubbles.default.withId(ChatColors.Id.Auto)
  }
  if (bubbleSolidColor != null) {
    return ChatColors(id = ChatColors.Id.NotSet, singleColor = bubbleSolidColor, linearGradient = null)
  }
  if (bubbleGradient != null) {
    return ChatColors(
      id = ChatColors.Id.NotSet,
      singleColor = null,
      linearGradient = ChatColors.LinearGradient(
        degrees = bubbleGradient.angle.toFloat(),
        colors = bubbleGradient.colors.toIntArray(),
        positions = floatArrayOf(0f, 1f)
      )
    )
  }
  return null
}

private fun ChatColors.tryToMapToBackupPreset(): ChatStyle.BubbleColorPreset? {
  when (this) {
    // Solids
    ChatColorsPalette.Bubbles.CRIMSON -> return ChatStyle.BubbleColorPreset.SOLID_CRIMSON
    ChatColorsPalette.Bubbles.VERMILION -> return ChatStyle.BubbleColorPreset.SOLID_VERMILION
    ChatColorsPalette.Bubbles.BURLAP -> return ChatStyle.BubbleColorPreset.SOLID_BURLAP
    ChatColorsPalette.Bubbles.FOREST -> return ChatStyle.BubbleColorPreset.SOLID_FOREST
    ChatColorsPalette.Bubbles.WINTERGREEN -> return ChatStyle.BubbleColorPreset.SOLID_WINTERGREEN
    ChatColorsPalette.Bubbles.TEAL -> return ChatStyle.BubbleColorPreset.SOLID_TEAL
    ChatColorsPalette.Bubbles.BLUE -> return ChatStyle.BubbleColorPreset.SOLID_BLUE
    ChatColorsPalette.Bubbles.INDIGO -> return ChatStyle.BubbleColorPreset.SOLID_INDIGO
    ChatColorsPalette.Bubbles.VIOLET -> return ChatStyle.BubbleColorPreset.SOLID_VIOLET
    ChatColorsPalette.Bubbles.PLUM -> return ChatStyle.BubbleColorPreset.SOLID_PLUM
    ChatColorsPalette.Bubbles.TAUPE -> return ChatStyle.BubbleColorPreset.SOLID_TAUPE
    ChatColorsPalette.Bubbles.STEEL -> return ChatStyle.BubbleColorPreset.SOLID_STEEL
    // Gradients
    ChatColorsPalette.Bubbles.EMBER -> return ChatStyle.BubbleColorPreset.GRADIENT_EMBER
    ChatColorsPalette.Bubbles.MIDNIGHT -> return ChatStyle.BubbleColorPreset.GRADIENT_MIDNIGHT
    ChatColorsPalette.Bubbles.INFRARED -> return ChatStyle.BubbleColorPreset.GRADIENT_INFRARED
    ChatColorsPalette.Bubbles.LAGOON -> return ChatStyle.BubbleColorPreset.GRADIENT_LAGOON
    ChatColorsPalette.Bubbles.FLUORESCENT -> return ChatStyle.BubbleColorPreset.GRADIENT_FLUORESCENT
    ChatColorsPalette.Bubbles.BASIL -> return ChatStyle.BubbleColorPreset.GRADIENT_BASIL
    ChatColorsPalette.Bubbles.SUBLIME -> return ChatStyle.BubbleColorPreset.GRADIENT_SUBLIME
    ChatColorsPalette.Bubbles.SEA -> return ChatStyle.BubbleColorPreset.GRADIENT_SEA
    ChatColorsPalette.Bubbles.TANGERINE -> return ChatStyle.BubbleColorPreset.GRADIENT_TANGERINE
  }
  return null
}
