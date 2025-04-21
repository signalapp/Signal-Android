/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.importer

import androidx.core.content.contentValuesOf
import org.signal.core.util.SqlUtil
import org.signal.core.util.insertInto
import org.signal.core.util.toInt
import org.thoughtcrime.securesms.attachments.AttachmentId
import org.thoughtcrime.securesms.backup.v2.ImportState
import org.thoughtcrime.securesms.backup.v2.database.restoreWallpaperAttachment
import org.thoughtcrime.securesms.backup.v2.proto.Chat
import org.thoughtcrime.securesms.backup.v2.util.parseChatWallpaper
import org.thoughtcrime.securesms.backup.v2.util.toLocal
import org.thoughtcrime.securesms.backup.v2.util.toLocalAttachment
import org.thoughtcrime.securesms.conversation.colors.ChatColors
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.ThreadTable
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.wallpaper.UriChatWallpaper
import kotlin.time.Duration.Companion.milliseconds

/**
 * Handles the importing of [Chat] models into the local database.
 */
object ChatArchiveImporter {
  fun import(chat: Chat, recipientId: RecipientId, importState: ImportState): Long {
    val chatColor = chat.style?.toLocal(importState)

    val wallpaperAttachmentId: AttachmentId? = chat.style?.wallpaperPhoto?.let { filePointer ->
      filePointer.toLocalAttachment(importState)?.let {
        SignalDatabase.attachments.restoreWallpaperAttachment(it)
      }
    }

    val chatWallpaper = chat.style?.parseChatWallpaper(wallpaperAttachmentId)

    val threadId = SignalDatabase.writableDatabase
      .insertInto(ThreadTable.TABLE_NAME)
      .values(
        ThreadTable.RECIPIENT_ID to recipientId.serialize(),
        ThreadTable.PINNED_ORDER to chat.pinnedOrder,
        ThreadTable.ARCHIVED to chat.archived.toInt(),
        ThreadTable.READ to if (chat.markedUnread) ThreadTable.ReadStatus.FORCED_UNREAD.serialize() else ThreadTable.ReadStatus.READ.serialize(),
        ThreadTable.ACTIVE to 1
      )
      .run()

    SignalDatabase.writableDatabase
      .update(
        RecipientTable.TABLE_NAME,
        contentValuesOf(
          RecipientTable.MENTION_SETTING to (if (chat.dontNotifyForMentionsIfMuted) RecipientTable.MentionSetting.DO_NOT_NOTIFY.id else RecipientTable.MentionSetting.ALWAYS_NOTIFY.id),
          RecipientTable.MUTE_UNTIL to (chat.muteUntilMs ?: 0),
          RecipientTable.MESSAGE_EXPIRATION_TIME to (chat.expirationTimerMs?.milliseconds?.inWholeSeconds ?: 0),
          RecipientTable.MESSAGE_EXPIRATION_TIME_VERSION to chat.expireTimerVersion,
          RecipientTable.CHAT_COLORS to chatColor?.serialize()?.encode(),
          RecipientTable.CUSTOM_CHAT_COLORS_ID to (chatColor?.id ?: ChatColors.Id.NotSet).longValue,
          RecipientTable.WALLPAPER_URI to if (chatWallpaper is UriChatWallpaper) chatWallpaper.uri.toString() else null,
          RecipientTable.WALLPAPER to chatWallpaper?.serialize()?.encode()
        ),
        "${RecipientTable.ID} = ?",
        SqlUtil.buildArgs(recipientId.toLong())
      )

    return threadId
  }
}
