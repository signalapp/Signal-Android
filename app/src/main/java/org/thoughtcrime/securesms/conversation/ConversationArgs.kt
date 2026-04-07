/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.thoughtcrime.securesms.conversation

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import org.signal.core.models.UriSerializer
import org.signal.core.models.media.Media
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.conversation.ConversationIntents.ConversationScreenType
import org.thoughtcrime.securesms.mms.SlideFactory
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.stickers.StickerLocator

@Serializable
@Parcelize
data class ConversationArgs(
  val recipientId: RecipientId,
  @JvmField val threadId: Long,
  val draftText: String?,
  @Serializable(with = UriSerializer::class) val draftMedia: Uri?,
  val draftContentType: String?,
  val media: List<Media?>?,
  val stickerLocator: StickerLocator?,
  val isBorderless: Boolean,
  val distributionType: Int,
  val startingPosition: Int,
  val isFirstTimeInSelfCreatedGroup: Boolean,
  val isWithSearchOpen: Boolean,
  val giftBadge: Badge?,
  val shareDataTimestamp: Long,
  val conversationScreenType: ConversationScreenType,
  val isIncognito: Boolean = false
) : Parcelable {
  @IgnoredOnParcel
  val draftMediaType: SlideFactory.MediaType? = SlideFactory.MediaType.from(draftContentType)

  fun canInitializeFromDatabase(): Boolean {
    return draftText == null && (draftMedia == null || ConversationIntents.isBubbleIntentUri(draftMedia) || ConversationIntents.isNotificationIntentUri(draftMedia)) && draftMediaType == null
  }
}
