/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2

import org.thoughtcrime.securesms.database.CallTable

/**
 * These represent situations where we will skip exporting a data frame due to the data being invalid.
 */
object ExportSkips {
  fun emptyChatItem(sentTimestamp: Long): String {
    return log(sentTimestamp, "Completely empty ChatItem (no inner item is set).")
  }

  fun emptyStandardMessage(sentTimestamp: Long): String {
    return log(sentTimestamp, "Completely empty StandardMessage (no body or attachments).")
  }

  fun invalidLongTextChatItem(sentTimestamp: Long): String {
    return log(sentTimestamp, "ChatItem with a long-text attachment had no body.")
  }

  fun messageExpiresTooSoon(sentTimestamp: Long): String {
    return log(sentTimestamp, "Message expires too soon. Must skip.")
  }

  fun individualCallStateNotMappable(sentTimestamp: Long, event: CallTable.Event): String {
    return log(sentTimestamp, "Unable to map group only status to 1:1 call state. Event: ${event.name}")
  }

  fun failedToParseSharedContact(sentTimestamp: Long): String {
    return log(sentTimestamp, "Failed to parse shared contacts.")
  }

  fun failedToParseGiftBadge(sentTimestamp: Long): String {
    return log(sentTimestamp, "Failed to parse GiftBadge.")
  }

  fun failedToParseGroupUpdate(sentTimestamp: Long): String {
    return log(sentTimestamp, "Failed to parse GroupUpdate.")
  }

  fun groupUpdateHasNoUpdates(sentTimestamp: Long): String {
    return log(sentTimestamp, "Group update record is parseable, but has no updates.")
  }

  fun directStoryReplyHasNoBody(sentTimestamp: Long): String {
    return log(sentTimestamp, "Direct story reply has no body.")
  }

  fun directStoryReplyInNoteToSelf(sentTimestamp: Long): String {
    return log(sentTimestamp, "Direct story reply in Note to Self.")
  }

  fun invalidChatItemStickerPackId(sentTimestamp: Long): String {
    return log(sentTimestamp, "Sticker message had an invalid packId.")
  }

  fun invalidChatItemStickerPackKey(sentTimestamp: Long): String {
    return log(sentTimestamp, "Sticker message had an invalid packKey.")
  }

  fun invalidStickerPackId(): String {
    return log(0, "Sticker pack had an invalid packId.")
  }

  fun invalidStickerPackKey(): String {
    return log(0, "Sticker pack  had an invalid packKey.")
  }

  fun identityUpdateForSelf(sentTimestamp: Long): String {
    return log(sentTimestamp, "Identity update for ourselves.")
  }

  fun identityDefaultForSelf(sentTimestamp: Long): String {
    return log(sentTimestamp, "Identity default update for ourselves.")
  }

  fun identityVerifiedForSelf(sentTimestamp: Long): String {
    return log(sentTimestamp, "Identity verified update for ourselves.")
  }

  fun fromRecipientIsNotAnIndividual(sentTimestamp: Long): String {
    return log(sentTimestamp, "The fromRecipient does not represent an individual person.")
  }

  fun oneOnOneMessageInTheWrongChat(sentTimestamp: Long): String {
    return log(sentTimestamp, "A 1:1 message is located in the wrong chat.")
  }

  fun paymentNotificationInNoteToSelf(sentTimestamp: Long): String {
    return log(sentTimestamp, "Payment notification is in Note to Self.")
  }

  fun profileChangeInNoteToSelf(sentTimestamp: Long): String {
    return log(sentTimestamp, "Profile change in Note to Self.")
  }

  fun profileChangeFromSelf(sentTimestamp: Long): String {
    return log(sentTimestamp, "Profile change from self.")
  }

  fun emptyProfileNameChange(sentTimestamp: Long): String {
    return log(sentTimestamp, "Profile name change was empty.")
  }

  fun emptyLearnedProfileChange(sentTimestamp: Long): String {
    return log(sentTimestamp, "Learned profile update was empty.")
  }

  fun invalidE164InThreadMerge(sentTimestamp: Long): String {
    return log(sentTimestamp, "Invalid e164 in thread merge event.")
  }

  fun failedToParseThreadMergeEvent(sentTimestamp: Long): String {
    return log(sentTimestamp, "Failed to parse thread merge event.")
  }

  private fun log(sentTimestamp: Long, message: String): String {
    return "[SKIP][$sentTimestamp] $message"
  }
}

/**
 * These represent situations where we encounter some weird data, but are still able to export the frame. We may have needed to "massage" the data to get
 * it to fit the spec.
 */
object ExportOddities {

  fun revisionsOnUnexpectedMessageType(sentTimestamp: Long): String {
    return log(sentTimestamp, "Attempted to set revisions on message that doesn't support it. Ignoring revisions.")
  }

  fun mismatchedRevisionHistory(sentTimestamp: Long): String {
    return log(sentTimestamp, "Revisions for this message contained items of a different type than the parent item. Ignoring mismatched revisions.")
  }

  fun outgoingMessageWasSentButTimerNotStarted(sentTimestamp: Long): String {
    return log(sentTimestamp, "Outgoing expiring message was sent, but the timer wasn't started. Setting expireStartDate to dateReceived.")
  }

  fun incomingMessageWasReadButTimerNotStarted(sentTimestamp: Long): String {
    return log(sentTimestamp, "Incoming expiring message was read, but the timer wasn't started. Setting expireStartDate to dateReceived.")
  }

  fun failedToParseBodyRangeList(sentTimestamp: Long): String {
    return log(sentTimestamp, "Unable to parse BodyRangeList. Ignoring it.")
  }

  fun failedToParseLinkPreview(sentTimestamp: Long): String {
    return log(sentTimestamp, "Failed to parse link preview. Ignoring it.")
  }

  fun distributionListAllExceptWithNoMembers(): String {
    return log(0, "Distribution list had a privacy mode of ALL_EXCEPT with no members. Exporting at ALL.")
  }

  fun distributionListHadSelfAsMember(): String {
    return log(0, "Distribution list had self as a member. Removing it.")
  }

  fun emptyQuote(sentTimestamp: Long): String {
    return log(sentTimestamp, "Quote had no text or attachments. Removing it.")
  }

  fun invalidE164InSessionSwitchover(sentTimestamp: Long): String {
    return log(sentTimestamp, "Invalid e164 in sessions switchover event. Exporting an empty event.")
  }

  fun undownloadedLongTextAttachment(sentTimestamp: Long): String {
    return log(sentTimestamp, "Long text attachment was not yet downloaded. Falling back to the known body with an attachment pointer.")
  }

  fun unreadableLongTextAttachment(sentTimestamp: Long): String {
    return log(sentTimestamp, "Long text attachment was unreadable. Dropping the pointer.")
  }

  fun unopenableLongTextAttachment(sentTimestamp: Long): String {
    return log(sentTimestamp, "Long text attachment failed to open. Falling back to the known body with an attachment pointer.")
  }

  fun bodyGreaterThanMaxLength(sentTimestamp: Long, length: Int): String {
    return log(sentTimestamp, "The body length was greater than the max allowed ($length bytes). Trimming to fit.")
  }

  private fun log(sentTimestamp: Long, message: String): String {
    return "[ODDITY][$sentTimestamp] $message"
  }
}

/**
 * These represent situations where we will skip importing a data frame due to the data being invalid.
 */
object ImportSkips {
  fun fromRecipientNotFound(sentTimestamp: Long): String {
    return log(sentTimestamp, "Failed to find the fromRecipient for the message.")
  }

  fun chatIdLocalRecipientNotFound(sentTimestamp: Long, chatId: Long): String {
    return log(sentTimestamp, "Failed to find a local recipientId for the provided chatId. ChatId in backup: $chatId")
  }

  fun chatIdRemoteRecipientNotFound(sentTimestamp: Long, chatId: Long): String {
    return log(sentTimestamp, "Failed to find a remote recipientId for the provided chatId. ChatId in backup: $chatId")
  }

  fun chatIdThreadNotFound(sentTimestamp: Long, chatId: Long): String {
    return log(sentTimestamp, "Failed to find a threadId for the provided chatId. ChatId in backup: $chatId")
  }

  fun chatFolderIdNotFound(): String {
    return log(0, "Failed to parse chatFolderId for the provided chat folder.")
  }

  fun notificationProfileIdNotFound(): String {
    return log(0, "Failed to parse notificationProfileId for the provided notification profile.")
  }

  private fun log(sentTimestamp: Long, message: String): String {
    return "[SKIP][$sentTimestamp] $message"
  }
}
