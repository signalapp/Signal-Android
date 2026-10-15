/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediakeyboard

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.signal.core.util.ThreadUtil
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.emoji.EmojiUtil
import org.signal.mediakeyboard.data.EmojiCategoryPage
import org.signal.mediakeyboard.data.EmojiKeyboardCategory
import org.signal.mediakeyboard.data.EmojiKeyboardRepository
import org.signal.mediakeyboard.data.KeyboardEmoji
import org.thoughtcrime.securesms.components.emoji.RecentEmojiPageModel
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.sms.MessageSender

/**
 * [EmojiKeyboardRepository] for the emoji sheet. Leaves out emoticons and leads with the reactions on [messageId].
 * Only [toggleReaction] adds to recents.
 *
 * @param recentsStorageKey Reactions keep their own recents, apart from the keyboard's.
 */
class SignalEmojiSheetRepository private constructor(
  private val context: Context,
  private val recentEmoji: RecentEmojiPageModel,
  private val messageId: MessageId?,
  private val delegate: SignalEmojiKeyboardRepository = SignalEmojiKeyboardRepository(context, recentEmoji)
) : EmojiKeyboardRepository by delegate {

  constructor(context: Context, recentsStorageKey: String, messageId: MessageId?) : this(
    context = context,
    recentEmoji = RecentEmojiPageModel(context, recentsStorageKey),
    messageId = messageId
  )

  override suspend fun getEmojiPages(): List<EmojiCategoryPage> {
    val thisMessage = withContext(Dispatchers.Default) {
      messageId?.let { id -> SignalDatabase.reactions.getReactions(id).map { it.emoji }.distinct() }.orEmpty()
    }

    return buildList {
      if (thisMessage.isNotEmpty()) {
        add(EmojiCategoryPage(EmojiKeyboardCategory.THIS_MESSAGE, thisMessage.map { KeyboardEmoji(it) }))
      }
      addAll(delegate.getEmojiPages().filter { it.category != EmojiKeyboardCategory.EMOTICONS })
    }
  }

  override suspend fun getRecentEmoji(): List<KeyboardEmoji> {
    return delegate.getRecentEmoji().filter { EmojiUtil.isEmoji(it.canonical) }
  }

  override suspend fun onEmojiUsed(emoji: String) = Unit

  /** Reacts to [messageId] with [emoji], or removes the user's reaction if it is already [emoji]. */
  fun toggleReaction(emoji: String) {
    val messageId = messageId ?: return

    SignalExecutors.BOUNDED.execute {
      val existing = SignalDatabase.reactions.getReactions(messageId).firstOrNull { it.author == Recipient.self().id }

      if (existing != null && existing.emoji == emoji) {
        MessageSender.sendReactionRemoval(context, messageId, existing)
      } else {
        MessageSender.sendNewReaction(context, messageId, emoji)
        ThreadUtil.runOnMain { recentEmoji.onCodePointSelected(emoji) }
      }
    }
  }
}
