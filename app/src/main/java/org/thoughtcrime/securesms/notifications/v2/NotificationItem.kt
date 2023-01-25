package org.thoughtcrime.securesms.notifications.v2

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.TextUtils
import androidx.annotation.StringRes
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.contactshare.Contact
import org.thoughtcrime.securesms.contactshare.ContactUtil
import org.thoughtcrime.securesms.database.MentionUtil
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.ThreadBodyUtil
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.database.model.ReactionRecord
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.mms.Slide
import org.thoughtcrime.securesms.mms.SlideDeck
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientUtil
import org.thoughtcrime.securesms.service.KeyCachingService
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.SpanUtil
import org.thoughtcrime.securesms.util.Util
import org.thoughtcrime.securesms.util.hasGiftBadge
import org.thoughtcrime.securesms.util.hasSharedContact
import org.thoughtcrime.securesms.util.hasSticker
import org.thoughtcrime.securesms.util.isMediaMessage
import org.thoughtcrime.securesms.util.isStoryReaction

private val TAG: String = Log.tag(NotificationItem::class.java)
private const val EMOJI_REPLACEMENT_STRING = "__EMOJI__"
private const val MAX_DISPLAY_LENGTH = 500

/**
 * Base for messaged-based notifications. Represents a single notification.
 */
sealed class NotificationItem(val threadRecipient: Recipient, protected val record: MessageRecord) : Comparable<NotificationItem> {

  val id: Long = record.id
  val thread = ConversationId.fromMessageRecord(record)
  val isMms: Boolean = record.isMms
  val slideDeck: SlideDeck? = if (record.isViewOnce) null else (record as? MmsMessageRecord)?.slideDeck
  val isJoined: Boolean = record.isJoined
  val isPersonSelf: Boolean
    get() = individualRecipient.isSelf

  protected val notifiedTimestamp: Long = record.notifiedTimestamp

  abstract val timestamp: Long
  abstract val individualRecipient: Recipient
  abstract val isNewNotification: Boolean

  protected abstract fun getPrimaryTextActual(context: Context): CharSequence
  abstract fun getStartingPosition(context: Context): Int
  abstract fun getLargeIconUri(): Uri?
  abstract fun getBigPictureUri(): Uri?
  abstract fun getThumbnailInfo(context: Context): ThumbnailInfo
  abstract fun canReply(context: Context): Boolean

  protected fun getMessageContentType(messageRecord: MmsMessageRecord): String {
    val thumbnailSlide: Slide? = messageRecord.slideDeck.thumbnailSlide

    return if (thumbnailSlide == null) {
      val slide: Slide? = messageRecord.slideDeck.firstSlide

      if (slide != null && slide.isVideoGif) {
        MediaUtil.IMAGE_GIF
      } else if (slide != null) {
        slide.contentType
      } else {
        Log.w(TAG, "Could not distinguish content type from message record, defaulting to JPEG")
        MediaUtil.IMAGE_JPEG
      }
    } else {
      if (thumbnailSlide.isVideoGif) {
        MediaUtil.IMAGE_GIF
      } else {
        thumbnailSlide.contentType
      }
    }
  }

  fun getStyledPrimaryText(context: Context, trimmed: Boolean = false): CharSequence {
    return if (SignalStore.settings().messageNotificationsPrivacy.isDisplayNothing) {
      context.getString(R.string.SingleRecipientNotificationBuilder_new_message)
    } else {
      SpannableStringBuilder().apply {
        append(Util.getBoldedString(individualRecipient.getShortDisplayNameIncludingUsername(context)))
        if (threadRecipient != individualRecipient) {
          append(Util.getBoldedString("@${threadRecipient.getDisplayName(context)}"))
        }
        append(": ")
        append(getPrimaryText(context).apply { if (trimmed) trimToDisplayLength() })
      }
    }
  }

  fun getPersonName(context: Context): CharSequence {
    return if (SignalStore.settings().messageNotificationsPrivacy.isDisplayContact) {
      individualRecipient.getDisplayName(context)
    } else {
      context.getString(R.string.SingleRecipientNotificationBuilder_signal)
    }
  }

  override fun compareTo(other: NotificationItem): Int {
    return timestamp.compareTo(other.timestamp)
  }

  fun getPersonUri(): String? {
    return if (SignalStore.settings().messageNotificationsPrivacy.isDisplayContact && individualRecipient.isSystemContact) {
      individualRecipient.contactUri.toString()
    } else {
      null
    }
  }

  fun getPersonIcon(context: Context): Bitmap? {
    return if (SignalStore.settings().messageNotificationsPrivacy.isDisplayContact) {
      individualRecipient.getContactDrawable(context).toLargeBitmap(context)
    } else {
      null
    }
  }

  fun getPrimaryText(context: Context): CharSequence {
    return if (SignalStore.settings().messageNotificationsPrivacy.isDisplayMessage) {
      if (RecipientUtil.isMessageRequestAccepted(context, thread.threadId)) {
        getPrimaryTextActual(context)
      } else {
        SpanUtil.italic(context.getString(R.string.SingleRecipientNotificationBuilder_message_request))
      }
    } else {
      context.getString(R.string.SingleRecipientNotificationBuilder_new_message)
    }
  }

  fun getInboxLine(context: Context): CharSequence? {
    return when {
      SignalStore.settings().messageNotificationsPrivacy.isDisplayNothing -> null
      else -> getStyledPrimaryText(context, true)
    }
  }

  open fun hasSameContent(other: NotificationItem): Boolean {
    return timestamp == other.timestamp &&
      id == other.id &&
      isMms == other.isMms &&
      individualRecipient == other.individualRecipient &&
      individualRecipient.hasSameContent(other.individualRecipient) &&
      slideDeck?.thumbnailSlide?.isInProgress == other.slideDeck?.thumbnailSlide?.isInProgress &&
      record.isRemoteDelete == other.record.isRemoteDelete
  }

  private fun CharSequence?.trimToDisplayLength(): CharSequence {
    val text: CharSequence = this ?: ""
    return if (text.length <= MAX_DISPLAY_LENGTH) {
      text
    } else {
      text.subSequence(0, MAX_DISPLAY_LENGTH)
    }
  }

  data class ThumbnailInfo(val uri: Uri? = null, val contentType: String? = null) {
    companion object {
      val NONE = ThumbnailInfo()
    }
  }
}

/**
 * Represents a notification associated with a new message.
 */
class MessageNotification(threadRecipient: Recipient, record: MessageRecord) : NotificationItem(threadRecipient, record) {
  override val timestamp: Long = record.timestamp
  override val individualRecipient: Recipient = if (record.isOutgoing) Recipient.self() else record.individualRecipient.resolve()
  override val isNewNotification: Boolean = notifiedTimestamp == 0L

  private var thumbnailInfo: ThumbnailInfo? = null

  override fun getPrimaryTextActual(context: Context): CharSequence {
    return if (KeyCachingService.isLocked(context)) {
      SpanUtil.italic(context.getString(R.string.MessageNotifier_locked_message))
    } else if (record.isMms && (record as MmsMessageRecord).sharedContacts.isNotEmpty()) {
      val contact = record.sharedContacts[0]
      ContactUtil.getStringSummary(context, contact)
    } else if (record.isMms && record.isViewOnce) {
      SpanUtil.italic(context.getString(getViewOnceDescription(record as MmsMessageRecord)))
    } else if (record.isRemoteDelete) {
      SpanUtil.italic(context.getString(R.string.MessageNotifier_this_message_was_deleted))
    } else if (record.isMms && !record.isMmsNotification && (record as MmsMessageRecord).slideDeck.slides.isNotEmpty()) {
      ThreadBodyUtil.getFormattedBodyFor(context, record).body
    } else if (record.isGroupCall) {
      MessageRecord.getGroupCallUpdateDescription(context, record.body, false).spannable
    } else if (record.hasGiftBadge()) {
      ThreadBodyUtil.getFormattedBodyFor(context, record).body
    } else if (record.isStoryReaction()) {
      ThreadBodyUtil.getFormattedBodyFor(context, record).body
    } else if (record.isPaymentNotification()) {
      ThreadBodyUtil.getFormattedBodyFor(context, record).body
    } else {
      MentionUtil.updateBodyWithDisplayNames(context, record) ?: ""
    }
  }

  @StringRes
  private fun getViewOnceDescription(messageRecord: MmsMessageRecord): Int {
    val contentType = getMessageContentType(messageRecord)
    return if (MediaUtil.isImageType(contentType)) R.string.MessageNotifier_view_once_photo else R.string.MessageNotifier_view_once_video
  }

  override fun getStartingPosition(context: Context): Int {
    return if (thread.groupStoryId != null) {
      SignalDatabase.messages.getMessagePositionInConversation(thread.threadId, thread.groupStoryId, record.dateReceived)
    } else {
      -1
    }
  }

  override fun getLargeIconUri(): Uri? {
    val slide: Slide? = slideDeck?.thumbnailSlide ?: slideDeck?.stickerSlide

    return if (slide?.isInProgress == false) slide.uri else null
  }

  override fun getBigPictureUri(): Uri? {
    val slide: Slide? = slideDeck?.thumbnailSlide

    return if (slide?.isInProgress == false) slide.uri else null
  }

  override fun getThumbnailInfo(context: Context): ThumbnailInfo {
    if (thumbnailInfo == null) {
      thumbnailInfo = if (SignalStore.settings().messageNotificationsPrivacy.isDisplayMessage && !KeyCachingService.isLocked(context)) {
        NotificationThumbnails.get(context, this)
      } else {
        ThumbnailInfo()
      }
    }

    return thumbnailInfo!!
  }

  override fun canReply(context: Context): Boolean {
    if (KeyCachingService.isLocked(context) ||
      record.isRemoteDelete ||
      record.isGroupCall ||
      record.isViewOnce ||
      record.isJoined
    ) {
      return false
    }

    if (record is MmsMessageRecord) {
      return (record.isMmsNotification || record.slideDeck.slides.isEmpty()) && record.sharedContacts.isEmpty()
    }

    return true
  }

  override fun hasSameContent(other: NotificationItem): Boolean {
    return super.hasSameContent(other) && thumbnailInfo == (other as? MessageNotification)?.thumbnailInfo
  }

  override fun toString(): String {
    return "MessageNotification(timestamp=$timestamp, isNewNotification=$isNewNotification)"
  }
}

/**
 * Represents a notification associated with a new reaction.
 */
class ReactionNotification(threadRecipient: Recipient, record: MessageRecord, val reaction: ReactionRecord) : NotificationItem(threadRecipient, record) {
  override val timestamp: Long = reaction.dateReceived
  override val individualRecipient: Recipient = Recipient.resolved(reaction.author)
  override val isNewNotification: Boolean = timestamp > notifiedTimestamp

  override fun getPrimaryTextActual(context: Context): CharSequence {
    return if (KeyCachingService.isLocked(context)) {
      SpanUtil.italic(context.getString(R.string.MessageNotifier_locked_message))
    } else {
      val text: String = SpanUtil.italic(getReactionMessageBody(context)).toString()
      val parts: Array<String> = text.split(EMOJI_REPLACEMENT_STRING).toTypedArray()
      val builder = SpannableStringBuilder()

      parts.forEachIndexed { i, part ->
        builder.append(SpanUtil.italic(part))
        if (i != parts.size - 1) {
          builder.append(reaction.emoji)
        }
      }

      builder
    }
  }

  private fun getReactionMessageBody(context: Context): CharSequence {
    val body: CharSequence = MentionUtil.updateBodyWithDisplayNames(context, record) ?: ""
    val bodyIsEmpty: Boolean = TextUtils.isEmpty(body)

    return if (record.hasSharedContact()) {
      val contact: Contact = (record as MmsMessageRecord).sharedContacts[0]
      val summary: CharSequence = ContactUtil.getStringSummary(context, contact)
      context.getString(R.string.MessageNotifier_reacted_s_to_s, EMOJI_REPLACEMENT_STRING, summary)
    } else if (record.hasSticker()) {
      context.getString(R.string.MessageNotifier_reacted_s_to_your_sticker, EMOJI_REPLACEMENT_STRING)
    } else if (record.isMms && record.isViewOnce) {
      context.getString(R.string.MessageNotifier_reacted_s_to_your_view_once_media, EMOJI_REPLACEMENT_STRING)
    } else if (!bodyIsEmpty) {
      context.getString(R.string.MessageNotifier_reacted_s_to_s, EMOJI_REPLACEMENT_STRING, body)
    } else if (record.isMediaMessage() && MediaUtil.isVideoType(getMessageContentType((record as MmsMessageRecord)))) {
      context.getString(R.string.MessageNotifier_reacted_s_to_your_video, EMOJI_REPLACEMENT_STRING)
    } else if (record.isMediaMessage() && MediaUtil.isGif(getMessageContentType((record as MmsMessageRecord)))) {
      context.getString(R.string.MessageNotifier_reacted_s_to_your_gif, EMOJI_REPLACEMENT_STRING)
    } else if (record.isMediaMessage() && MediaUtil.isImageType(getMessageContentType((record as MmsMessageRecord)))) {
      context.getString(R.string.MessageNotifier_reacted_s_to_your_image, EMOJI_REPLACEMENT_STRING)
    } else if (record.isMediaMessage() && MediaUtil.isAudioType(getMessageContentType((record as MmsMessageRecord)))) {
      context.getString(R.string.MessageNotifier_reacted_s_to_your_audio, EMOJI_REPLACEMENT_STRING)
    } else if (record.isMediaMessage()) {
      context.getString(R.string.MessageNotifier_reacted_s_to_your_file, EMOJI_REPLACEMENT_STRING)
    } else {
      context.getString(R.string.MessageNotifier_reacted_s_to_s, EMOJI_REPLACEMENT_STRING, body)
    }
  }

  override fun getStartingPosition(context: Context): Int {
    return SignalDatabase.messages.getMessagePositionInConversation(thread.threadId, thread.groupStoryId ?: 0L, record.dateReceived)
  }

  override fun getLargeIconUri(): Uri? = null
  override fun getBigPictureUri(): Uri? = null
  override fun getThumbnailInfo(context: Context): ThumbnailInfo = ThumbnailInfo()
  override fun canReply(context: Context): Boolean = false

  override fun toString(): String {
    return "ReactionNotification(timestamp=$timestamp, isNewNotification=$isNewNotification)"
  }
}
