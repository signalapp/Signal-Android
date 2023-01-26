@file:JvmName("MessageRecordUtil")

package org.thoughtcrime.securesms.util

import android.content.Context
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.MessageTypes
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.database.model.Quote
import org.thoughtcrime.securesms.database.model.databaseprotos.GiftBadge
import org.thoughtcrime.securesms.mms.QuoteModel
import org.thoughtcrime.securesms.mms.TextSlide
import org.thoughtcrime.securesms.stickers.StickerUrl

const val MAX_BODY_DISPLAY_LENGTH = 1000

fun MessageRecord.isMediaMessage(): Boolean {
  return isMms &&
    !isMmsNotification &&
    (this as MediaMmsMessageRecord).containsMediaSlide() &&
    slideDeck.stickerSlide == null
}

fun MessageRecord.hasSticker(): Boolean =
  isMms && (this as MmsMessageRecord).slideDeck.stickerSlide != null

fun MessageRecord.hasSharedContact(): Boolean =
  isMms && (this as MmsMessageRecord).sharedContacts.isNotEmpty()

fun MessageRecord.hasLocation(): Boolean =
  isMms && ((this as MmsMessageRecord).slideDeck.slides).any { slide -> slide.hasLocation() }

fun MessageRecord.hasAudio(): Boolean =
  isMms && (this as MmsMessageRecord).slideDeck.audioSlide != null

fun MessageRecord.isCaptionlessMms(context: Context): Boolean =
  isMms && isDisplayBodyEmpty(context) && (this as MmsMessageRecord).slideDeck.textSlide == null

fun MessageRecord.hasThumbnail(): Boolean =
  isMms && (this as MmsMessageRecord).slideDeck.thumbnailSlide != null

fun MessageRecord.isStoryReaction(): Boolean =
  isMms && MessageTypes.isStoryReaction(type)

fun MessageRecord.isStory(): Boolean =
  isMms && (this as MmsMessageRecord).storyType.isStory

fun MessageRecord.isBorderless(context: Context): Boolean {
  return isCaptionlessMms(context) &&
    hasThumbnail() &&
    (this as MmsMessageRecord).slideDeck.thumbnailSlide?.isBorderless == true
}

fun MessageRecord.hasNoBubble(context: Context): Boolean =
  hasSticker() || isBorderless(context) || (isTextOnly(context) && isJumbomoji(context))

fun MessageRecord.hasOnlyThumbnail(context: Context): Boolean {
  return hasThumbnail() &&
    !hasAudio() &&
    !hasDocument() &&
    !hasSharedContact() &&
    !hasSticker() &&
    !isBorderless(context) &&
    !isViewOnceMessage()
}

fun MessageRecord.hasDocument(): Boolean =
  isMms && (this as MmsMessageRecord).slideDeck.documentSlide != null

fun MessageRecord.isViewOnceMessage(): Boolean =
  isMms && (this as MmsMessageRecord).isViewOnce

fun MessageRecord.hasExtraText(): Boolean {
  val hasTextSlide = isMms && (this as MmsMessageRecord).slideDeck.textSlide != null
  val hasOverflowText: Boolean = body.length > MAX_BODY_DISPLAY_LENGTH

  return hasTextSlide || hasOverflowText
}

fun MessageRecord.hasQuote(): Boolean =
  isMms && (this as MmsMessageRecord).quote != null

fun MessageRecord.getQuote(): Quote? =
  if (isMms) {
    (this as MmsMessageRecord).quote
  } else {
    null
  }

fun MessageRecord.hasLinkPreview(): Boolean =
  isMms && (this as MmsMessageRecord).linkPreviews.isNotEmpty()

fun MessageRecord.hasTextSlide(): Boolean =
  isMms && (this as MmsMessageRecord).slideDeck.textSlide != null && this.slideDeck.textSlide?.uri != null

fun MessageRecord.requireTextSlide(): TextSlide =
  requireNotNull((this as MmsMessageRecord).slideDeck.textSlide)

fun MessageRecord.hasBigImageLinkPreview(context: Context): Boolean {
  if (!hasLinkPreview()) {
    return false
  }

  val linkPreview = (this as MmsMessageRecord).linkPreviews[0]

  if (linkPreview.thumbnail.isPresent && !Util.isEmpty(linkPreview.description)) {
    return true
  }

  val minWidth = context.resources.getDimensionPixelSize(R.dimen.media_bubble_min_width_solo)

  return linkPreview.thumbnail.isPresent && linkPreview.thumbnail.get().width >= minWidth && !StickerUrl.isValidShareLink(linkPreview.url)
}

fun MessageRecord.hasGiftBadge(): Boolean {
  return (this as? MmsMessageRecord)?.giftBadge != null
}

fun MessageRecord.requireGiftBadge(): GiftBadge {
  return (this as MmsMessageRecord).giftBadge!!
}

fun MessageRecord.isTextOnly(context: Context): Boolean {
  return !isMms ||
    (
      !isViewOnceMessage() &&
        !hasLinkPreview() &&
        !hasQuote() &&
        !hasExtraText() &&
        !hasDocument() &&
        !hasThumbnail() &&
        !hasAudio() &&
        !hasLocation() &&
        !hasSharedContact() &&
        !hasSticker() &&
        !isCaptionlessMms(context) &&
        !hasGiftBadge() &&
        !isPaymentNotification()
      )
}

fun MessageRecord.isScheduled(): Boolean {
  return (this as? MediaMmsMessageRecord)?.scheduledDate?.let { it != -1L } ?: false
}

/**
 * Returns the QuoteType for this record, as if it was being quoted.
 */
fun MessageRecord.getRecordQuoteType(): QuoteModel.Type {
  return if (hasGiftBadge()) QuoteModel.Type.GIFT_BADGE else QuoteModel.Type.NORMAL
}
