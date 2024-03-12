/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.text.SpannableStringBuilder
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.graphics.drawable.IconCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.core.SingleEmitter
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.StreamUtil
import org.signal.core.util.concurrent.MaybeCompat
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.dp
import org.signal.core.util.logging.Log
import org.signal.core.util.toOptional
import org.signal.paging.PagedData
import org.signal.paging.PagingConfig
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.ShortcutLauncherActivity
import org.thoughtcrime.securesms.attachments.TombstoneAttachment
import org.thoughtcrime.securesms.components.emoji.EmojiStrings
import org.thoughtcrime.securesms.components.reminder.BubbleOptOutReminder
import org.thoughtcrime.securesms.components.reminder.ExpiredBuildReminder
import org.thoughtcrime.securesms.components.reminder.GroupsV1MigrationSuggestionsReminder
import org.thoughtcrime.securesms.components.reminder.PendingGroupJoinRequestsReminder
import org.thoughtcrime.securesms.components.reminder.Reminder
import org.thoughtcrime.securesms.components.reminder.ServiceOutageReminder
import org.thoughtcrime.securesms.components.reminder.UnauthorizedReminder
import org.thoughtcrime.securesms.contactshare.Contact
import org.thoughtcrime.securesms.contactshare.ContactUtil
import org.thoughtcrime.securesms.conversation.ConversationMessage
import org.thoughtcrime.securesms.conversation.MessageSendType
import org.thoughtcrime.securesms.conversation.mutiselect.MultiselectPart
import org.thoughtcrime.securesms.conversation.v2.RequestReviewState.GroupReviewState
import org.thoughtcrime.securesms.conversation.v2.RequestReviewState.IndividualReviewState
import org.thoughtcrime.securesms.conversation.v2.data.ConversationDataSource
import org.thoughtcrime.securesms.crypto.ReentrantSessionLock
import org.thoughtcrime.securesms.database.GroupTable
import org.thoughtcrime.securesms.database.IdentityTable.VerifiedStatus
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.RxDatabaseObserver
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.attachments
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.recipients
import org.thoughtcrime.securesms.database.model.GroupRecord
import org.thoughtcrime.securesms.database.model.IdentityRecord
import org.thoughtcrime.securesms.database.model.Mention
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.database.model.Quote
import org.thoughtcrime.securesms.database.model.ReactionRecord
import org.thoughtcrime.securesms.database.model.StickerRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobs.MultiDeviceViewOnceOpenJob
import org.thoughtcrime.securesms.jobs.ServiceOutageDetectionJob
import org.thoughtcrime.securesms.keyboard.KeyboardUtil
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.linkpreview.LinkPreview
import org.thoughtcrime.securesms.messagerequests.MessageRequestState
import org.thoughtcrime.securesms.mms.OutgoingMessage
import org.thoughtcrime.securesms.mms.PartAuthority
import org.thoughtcrime.securesms.mms.QuoteModel
import org.thoughtcrime.securesms.mms.Slide
import org.thoughtcrime.securesms.mms.SlideDeck
import org.thoughtcrime.securesms.profiles.spoofing.ReviewUtil
import org.thoughtcrime.securesms.providers.BlobProvider
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.search.MessageResult
import org.thoughtcrime.securesms.sms.MessageSender
import org.thoughtcrime.securesms.sms.MessageSender.PreUploadResult
import org.thoughtcrime.securesms.util.BitmapUtil
import org.thoughtcrime.securesms.util.DrawableUtil
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.MessageUtil
import org.thoughtcrime.securesms.util.SignalLocalMetrics
import org.thoughtcrime.securesms.util.Util
import org.thoughtcrime.securesms.util.hasLinkPreview
import org.thoughtcrime.securesms.util.hasSharedContact
import org.thoughtcrime.securesms.util.hasTextSlide
import org.thoughtcrime.securesms.util.isViewOnceMessage
import org.thoughtcrime.securesms.util.requireTextSlide
import java.io.IOException
import java.util.Optional
import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class ConversationRepository(
  private val localContext: Context,
  private val isInBubble: Boolean
) {

  companion object {
    private val TAG = Log.tag(ConversationRepository::class.java)
  }

  private val applicationContext = localContext.applicationContext
  private val oldConversationRepository = org.thoughtcrime.securesms.conversation.ConversationRepository()

  /**
   * Gets image details for an image sent from the keyboard
   */
  fun getKeyboardImageDetails(uri: Uri): Maybe<KeyboardUtil.ImageDetails> {
    return MaybeCompat.fromCallable {
      KeyboardUtil.getImageDetails(Glide.with(applicationContext), uri)
    }.subscribeOn(Schedulers.io())
  }

  /**
   * Loads the details necessary to display the conversation thread.
   */
  fun getConversationThreadState(threadId: Long, requestedStartPosition: Int): Single<ConversationThreadState> {
    return Single.fromCallable {
      val recipient = SignalDatabase.threads.getRecipientForThreadId(threadId)!!

      SignalLocalMetrics.ConversationOpen.onMetadataLoadStarted()
      val metadata = oldConversationRepository.getConversationData(threadId, recipient, requestedStartPosition)
      SignalLocalMetrics.ConversationOpen.onMetadataLoaded()

      val messageRequestData = metadata.messageRequestData
      val dataSource = ConversationDataSource(
        localContext,
        threadId,
        messageRequestData,
        metadata.showUniversalExpireTimerMessage,
        metadata.threadSize
      )
      val config = PagingConfig.Builder().setPageSize(25)
        .setBufferPages(2)
        .setStartIndex(max(metadata.getStartPosition(), 0))
        .build()

      ConversationThreadState(
        items = PagedData.createForObservable(dataSource, config),
        meta = metadata
      )
    }.subscribeOn(Schedulers.io())
  }

  fun sendReactionRemoval(messageRecord: MessageRecord, oldRecord: ReactionRecord): Completable {
    return Completable.fromAction {
      MessageSender.sendReactionRemoval(
        applicationContext,
        MessageId(messageRecord.id),
        oldRecord
      )
    }.subscribeOn(Schedulers.io())
  }

  fun sendNewReaction(messageRecord: MessageRecord, emoji: String): Completable {
    return Completable.fromAction {
      MessageSender.sendNewReaction(
        applicationContext,
        MessageId(messageRecord.id),
        emoji
      )
    }.subscribeOn(Schedulers.io())
  }

  fun sendMessage(
    threadId: Long,
    threadRecipient: Recipient,
    metricId: String?,
    body: String,
    slideDeck: SlideDeck?,
    scheduledDate: Long,
    messageToEdit: MessageId?,
    quote: QuoteModel?,
    mentions: List<Mention>,
    bodyRanges: BodyRangeList?,
    contacts: List<Contact>,
    linkPreviews: List<LinkPreview>,
    preUploadResults: List<PreUploadResult>,
    isViewOnce: Boolean
  ): Completable {
    val sendCompletable = Completable.create { emitter ->
      val splitMessage: MessageUtil.SplitResult = MessageUtil.getSplitMessage(
        applicationContext,
        body,
        MessageSendType.SignalMessageSendType.calculateCharacters(body).maxPrimaryMessageSize
      )

      val outgoingMessageSlideDeck: SlideDeck? = splitMessage.textSlide.map {
        (slideDeck ?: SlideDeck()).apply {
          addSlide(it)
        }
      }.orElse(slideDeck)

      val message = OutgoingMessage(
        threadRecipient = threadRecipient,
        sentTimeMillis = System.currentTimeMillis(),
        body = if (slideDeck != null) OutgoingMessage.buildMessage(slideDeck, splitMessage.body) else splitMessage.body,
        expiresIn = threadRecipient.expiresInSeconds.seconds.inWholeMilliseconds,
        isUrgent = true,
        isSecure = true,
        bodyRanges = bodyRanges,
        scheduledDate = scheduledDate,
        outgoingQuote = quote,
        messageToEdit = messageToEdit?.id ?: 0,
        mentions = mentions,
        sharedContacts = contacts,
        linkPreviews = linkPreviews,
        attachments = outgoingMessageSlideDeck?.asAttachments() ?: emptyList(),
        isViewOnce = isViewOnce
      )

      if (preUploadResults.isEmpty()) {
        MessageSender.send(
          ApplicationDependencies.getApplication(),
          message,
          threadId,
          MessageSender.SendType.SIGNAL,
          metricId
        ) {
          emitter.onComplete()
        }
      } else {
        MessageSender.sendPushWithPreUploadedMedia(
          ApplicationDependencies.getApplication(),
          message,
          preUploadResults,
          threadId
        ) {
          emitter.onComplete()
        }
      }
    }

    return sendCompletable
      .subscribeOn(Schedulers.io())
  }

  fun setLastVisibleMessageTimestamp(threadId: Long, lastVisibleMessageTimestamp: Long) {
    SignalExecutors.BOUNDED_IO.execute { SignalDatabase.threads.setLastScrolled(threadId, lastVisibleMessageTimestamp) }
  }

  fun markGiftBadgeRevealed(messageId: Long) {
    oldConversationRepository.markGiftBadgeRevealed(messageId)
  }

  fun getQuotedMessagePosition(threadId: Long, quote: Quote): Single<Int> {
    return Single.fromCallable {
      SignalDatabase.messages.getQuotedMessagePosition(threadId, quote.id, quote.author)
    }.subscribeOn(Schedulers.io())
  }

  fun getMessageResultPosition(threadId: Long, messageResult: MessageResult): Single<Int> {
    return Single.fromCallable {
      SignalDatabase.messages.getMessagePositionInConversation(threadId, messageResult.receivedTimestampMs)
    }.subscribeOn(Schedulers.io())
  }

  fun getNextMentionPosition(threadId: Long): Single<Int> {
    return Single.fromCallable {
      val details = SignalDatabase.messages.getOldestUnreadMentionDetails(threadId)
      if (details == null) {
        -1
      } else {
        SignalDatabase.messages.getMessagePositionInConversation(threadId, details.second(), details.first())
      }
    }.subscribeOn(Schedulers.io())
  }

  fun getMessagePosition(threadId: Long, dateReceived: Long, authorId: RecipientId): Single<Int> {
    return Single.fromCallable {
      SignalDatabase.messages.getMessagePositionInConversation(threadId, dateReceived, authorId)
    }.subscribeOn(Schedulers.io())
  }

  fun getMessageCounts(threadId: Long): Flowable<MessageCounts> {
    return RxDatabaseObserver.conversation(threadId)
      .map { getUnreadCount(threadId) }
      .distinctUntilChanged()
      .map { MessageCounts(it, getUnreadMentionsCount(threadId)) }
  }

  private fun getUnreadCount(threadId: Long): Int {
    return SignalDatabase.messages.getUnreadCount(threadId)
  }

  private fun getUnreadMentionsCount(threadId: Long): Int {
    return SignalDatabase.messages.getUnreadMentionCount(threadId)
  }

  fun getReminder(groupRecord: GroupRecord?): Maybe<Optional<Reminder>> {
    return Maybe.fromCallable {
      val reminder: Reminder? = when {
        ExpiredBuildReminder.isEligible() -> ExpiredBuildReminder(applicationContext)
        UnauthorizedReminder.isEligible(applicationContext) -> UnauthorizedReminder()
        ServiceOutageReminder.isEligible(applicationContext) -> {
          ApplicationDependencies.getJobManager().add(ServiceOutageDetectionJob())
          ServiceOutageReminder()
        }

        groupRecord != null && groupRecord.actionableRequestingMembersCount > 0 -> {
          PendingGroupJoinRequestsReminder(groupRecord.actionableRequestingMembersCount)
        }

        groupRecord != null && groupRecord.gv1MigrationSuggestions.isNotEmpty() -> {
          GroupsV1MigrationSuggestionsReminder(groupRecord.gv1MigrationSuggestions)
        }

        isInBubble && !SignalStore.tooltips().hasSeenBubbleOptOutTooltip() && Build.VERSION.SDK_INT > 29 -> {
          BubbleOptOutReminder()
        }

        else -> null
      }

      reminder.toOptional()
    }
  }

  @Suppress("IfThenToElvis")
  fun getIdentityRecords(recipient: Recipient, groupRecord: GroupRecord?): Single<IdentityRecordsState> {
    return Single.fromCallable {
      val recipients = if (groupRecord == null) {
        listOf(recipient)
      } else if (groupRecord.isV2Group) {
        groupRecord.requireV2GroupProperties().getMemberRecipients(GroupTable.MemberSet.FULL_MEMBERS_EXCLUDING_SELF)
      } else {
        emptyList()
      }

      val records = ApplicationDependencies.getProtocolStore().aci().identities().getIdentityRecords(recipients)
      val isVerified = recipient.registered == RecipientTable.RegisteredState.REGISTERED &&
        Recipient.self().isRegistered &&
        records.isVerified &&
        !recipient.isSelf

      IdentityRecordsState(recipient, groupRecord, isVerified, records, isGroup = groupRecord != null)
    }.subscribeOn(Schedulers.io())
  }

  fun resetVerifiedStatusToDefault(unverifiedIdentities: List<IdentityRecord>): Completable {
    return Completable.fromCallable {
      ReentrantSessionLock.INSTANCE.acquire().use {
        val identityStore = ApplicationDependencies.getProtocolStore().aci().identities()
        for ((recipientId, identityKey) in unverifiedIdentities) {
          identityStore.setVerified(recipientId, identityKey, VerifiedStatus.DEFAULT)
        }
      }
    }.subscribeOn(Schedulers.io())
  }

  fun getRequestReviewState(recipient: Recipient, group: GroupRecord?, messageRequest: MessageRequestState): Single<RequestReviewState> {
    return Single.fromCallable {
      if (group == null && messageRequest.state != MessageRequestState.State.INDIVIDUAL) {
        return@fromCallable RequestReviewState()
      }

      if (group == null) {
        val recipientsToReview = ReviewUtil.getRecipientsToPromptForReview(recipient.id)
        if (recipientsToReview.size > 0) {
          return@fromCallable RequestReviewState(
            individualReviewState = IndividualReviewState(
              target = recipient,
              firstDuplicate = Recipient.resolvedList(recipientsToReview)[0]
            )
          )
        }
      }

      if (group != null && group.isV2Group) {
        val groupId = group.id.requireV2()
        val duplicateRecipients: List<Recipient> = ReviewUtil.getDuplicatedRecipients(groupId).map { it.recipient }

        if (duplicateRecipients.isNotEmpty()) {
          return@fromCallable RequestReviewState(
            groupReviewState = GroupReviewState(
              groupId,
              duplicateRecipients[0],
              duplicateRecipients[1],
              duplicateRecipients.size
            )
          )
        }
      }

      RequestReviewState()
    }.subscribeOn(Schedulers.io())
  }

  fun getTemporaryViewOnceUri(mmsMessageRecord: MmsMessageRecord): Maybe<Uri> {
    return MaybeCompat.fromCallable {
      Log.i(TAG, "Copying the view-once photo to temp storage and deleting underlying media.")

      try {
        val thumbnailSlide = mmsMessageRecord.slideDeck.thumbnailSlide ?: return@fromCallable null
        val thumbnailUri = thumbnailSlide.uri ?: return@fromCallable null

        val inputStream = PartAuthority.getAttachmentStream(applicationContext, thumbnailUri)
        val tempUri = BlobProvider.getInstance().forData(inputStream, thumbnailSlide.fileSize)
          .withMimeType(thumbnailSlide.contentType)
          .createForSingleSessionOnDisk(applicationContext)

        attachments.deleteAttachmentFilesForViewOnceMessage(mmsMessageRecord.id)
        ApplicationDependencies.getViewOnceMessageManager().scheduleIfNecessary()
        ApplicationDependencies.getJobManager().add(MultiDeviceViewOnceOpenJob(MessageTable.SyncMessageId(mmsMessageRecord.fromRecipient.id, mmsMessageRecord.dateSent)))

        tempUri
      } catch (e: IOException) {
        null
      }
    }.doOnComplete {
      Log.w(TAG, "Failed to open view-once photo. Deleting the attachments for the message just in case.")
      attachments.deleteAttachmentFilesForViewOnceMessage(mmsMessageRecord.id)
    }.subscribeOn(Schedulers.io())
  }

  fun setConversationMuted(recipientId: RecipientId, until: Long) {
    SignalExecutors.BOUNDED_IO.execute { recipients.setMuted(recipientId, until) }
  }

  /**
   * Copies the selected content to the clipboard. Maybe will emit either the copied contents or
   * a complete which means there were no contents to be copied.
   */
  fun copyToClipboard(context: Context, messageParts: Set<MultiselectPart>): Maybe<CharSequence> {
    return Maybe.fromCallable { extractBodies(context, messageParts) }
      .subscribeOn(Schedulers.computation())
      .observeOn(AndroidSchedulers.mainThread())
      .doOnSuccess {
        Util.copyToClipboard(context, it)
      }
  }

  fun resendMessage(messageRecord: MessageRecord): Completable {
    return Completable.fromAction {
      MessageSender.resend(applicationContext, messageRecord)
    }.subscribeOn(Schedulers.io())
  }

  private fun extractBodies(context: Context, messageParts: Set<MultiselectPart>): CharSequence {
    return messageParts
      .asSequence()
      .sortedBy { it.getMessageRecord().dateReceived }
      .map { it.conversationMessage }
      .distinct()
      .mapNotNull { message ->
        if (message.messageRecord.hasTextSlide()) {
          val textSlideUri = message.messageRecord.requireTextSlide().uri
          if (textSlideUri == null) {
            message.getDisplayBody(context)
          } else {
            try {
              PartAuthority.getAttachmentStream(context, textSlideUri).use {
                val body = StreamUtil.readFullyAsString(it)
                ConversationMessage.ConversationMessageFactory.createWithUnresolvedData(context, message.messageRecord, body, message.threadRecipient)
                  .getDisplayBody(context)
              }
            } catch (e: IOException) {
              Log.w(TAG, "failed to read text slide data.")
              null
            }
          }
        } else {
          message.getDisplayBody(context)
        }
      }
      .filterNot(Util::isEmpty)
      .joinTo(buffer = SpannableStringBuilder(), separator = "\n")
  }

  fun getRecipientContactPhotoBitmap(context: Context, requestManager: RequestManager, recipient: Recipient): Single<ShortcutInfoCompat> {
    val fallback = recipient.fallbackContactPhoto.asDrawable(context, recipient.avatarColor, false)

    return Single
      .create { emitter ->
        requestManager
          .asBitmap()
          .load(recipient.contactPhoto)
          .error(fallback)
          .into(ContactPhotoTarget(recipient.id, emitter))
      }
      .flatMap(ContactPhotoResult::transformToFinalBitmap)
      .map(IconCompat::createWithAdaptiveBitmap)
      .map {
        val name = if (recipient.isSelf) context.getString(R.string.note_to_self) else recipient.getDisplayName(context)

        ShortcutInfoCompat.Builder(context, "${recipient.id.serialize()}-${System.currentTimeMillis()}")
          .setShortLabel(name)
          .setIcon(it)
          .setIntent(ShortcutLauncherActivity.createIntent(context, recipient.id))
          .build()
      }
      .subscribeOn(Schedulers.computation())
  }

  fun getSlideDeckAndBodyForReply(context: Context, conversationMessage: ConversationMessage): Pair<SlideDeck, CharSequence> {
    val messageRecord = conversationMessage.messageRecord

    return if (messageRecord.isMms && messageRecord.hasSharedContact()) {
      val contact: Contact = (messageRecord as MmsMessageRecord).sharedContacts.first()
      val displayName: String = ContactUtil.getDisplayName(contact)
      val body: String = context.getString(R.string.ConversationActivity_quoted_contact_message, EmojiStrings.BUST_IN_SILHOUETTE, displayName)
      val slideDeck = SlideDeck()

      if (contact.avatarAttachment != null) {
        slideDeck.addSlide(MediaUtil.getSlideForAttachment(contact.avatarAttachment))
      }

      slideDeck to body
    } else if (messageRecord.isMms && messageRecord.hasLinkPreview()) {
      val linkPreview = (messageRecord as MmsMessageRecord).linkPreviews.first()
      val slideDeck = SlideDeck()

      linkPreview.thumbnail.ifPresent {
        slideDeck.addSlide(MediaUtil.getSlideForAttachment(it))
      }

      slideDeck to conversationMessage.getDisplayBody(context)
    } else {
      var slideDeck = if (messageRecord.isMms) {
        (messageRecord as MmsMessageRecord).slideDeck
      } else {
        SlideDeck()
      }

      if (messageRecord.isViewOnceMessage()) {
        val attachment = TombstoneAttachment(MediaUtil.VIEW_ONCE, true)
        slideDeck = SlideDeck()
        slideDeck.addSlide(MediaUtil.getSlideForAttachment(attachment))
      }

      slideDeck to conversationMessage.getDisplayBody(context)
    }
  }

  fun resolveMessageToEdit(conversationMessage: ConversationMessage): Single<ConversationMessage> {
    return oldConversationRepository.resolveMessageToEdit(conversationMessage)
  }

  fun deleteSlideData(slides: List<Slide>) {
    SignalExecutors.BOUNDED_IO.execute {
      slides
        .mapNotNull(Slide::getUri)
        .filter(BlobProvider::isAuthority)
        .forEach {
          BlobProvider.getInstance().delete(applicationContext, it)
        }
    }
  }

  fun updateStickerLastUsedTime(stickerRecord: StickerRecord, timestamp: Duration) {
    SignalExecutors.BOUNDED_IO.execute {
      SignalDatabase.stickers.updateStickerLastUsedTime(stickerRecord.rowId, timestamp.inWholeMilliseconds)
    }
  }

  fun startExpirationTimeout(expirationInfos: List<MessageTable.ExpirationInfo>) {
    SignalDatabase.messages.markExpireStarted(expirationInfos.map { it.id to it.expireStarted })
    ApplicationDependencies.getExpiringMessageManager().scheduleDeletion(expirationInfos)
  }

  fun markLastSeen(threadId: Long) {
    SignalExecutors.BOUNDED_IO.execute {
      SignalDatabase.threads.setLastSeen(threadId)
    }
  }

  /**
   * Glide target for a contact photo which expects an error drawable, and publishes
   * the result to the given emitter.
   *
   * The recipient is only used for displaying logging information.
   */
  private class ContactPhotoTarget(
    private val recipientId: RecipientId,
    private val emitter: SingleEmitter<ContactPhotoResult>
  ) : CustomTarget<Bitmap>() {
    override fun onLoadFailed(errorDrawable: Drawable?) {
      requireNotNull(errorDrawable)
      Log.w(TAG, "Utilizing fallback photo for shortcut for recipient $recipientId")
      emitter.onSuccess(ContactPhotoResult.DrawableResult(errorDrawable))
    }

    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
      emitter.onSuccess(ContactPhotoResult.BitmapResult(resource))
    }

    override fun onLoadCleared(placeholder: Drawable?) = Unit
  }

  /**
   * The result of the Glide load to get a user's contact photo. This can then be transformed into
   * something that the Android system likes via [transformToFinalBitmap]
   */
  sealed interface ContactPhotoResult {

    companion object {
      private val SHORTCUT_ICON_SIZE = if (Build.VERSION.SDK_INT >= 26) 72.dp else (48 + 16 * 2).dp
    }

    class DrawableResult(private val drawable: Drawable) : ContactPhotoResult {
      override fun transformToFinalBitmap(): Single<Bitmap> {
        return Single.create {
          val bitmap = DrawableUtil.toBitmap(drawable, SHORTCUT_ICON_SIZE, SHORTCUT_ICON_SIZE)
          it.setCancellable {
            bitmap.recycle()
          }
          it.onSuccess(bitmap)
        }
      }
    }

    class BitmapResult(private val bitmap: Bitmap) : ContactPhotoResult {
      override fun transformToFinalBitmap(): Single<Bitmap> {
        return Single.create {
          val bitmap = BitmapUtil.createScaledBitmap(bitmap, SHORTCUT_ICON_SIZE, SHORTCUT_ICON_SIZE)
          it.setCancellable {
            bitmap.recycle()
          }
          it.onSuccess(bitmap)
        }
      }
    }

    fun transformToFinalBitmap(): Single<Bitmap>
  }

  data class MessageCounts(
    val unread: Int,
    val mentions: Int
  )
}
