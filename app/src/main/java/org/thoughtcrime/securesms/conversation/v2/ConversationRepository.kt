package org.thoughtcrime.securesms.conversation.v2

import android.content.Context
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.paging.PagedData
import org.signal.paging.PagingConfig
import org.thoughtcrime.securesms.conversation.colors.GroupAuthorNameColorHelper
import org.thoughtcrime.securesms.conversation.colors.NameColor
import org.thoughtcrime.securesms.conversation.v2.data.ConversationDataSource
import org.thoughtcrime.securesms.database.RxDatabaseObserver
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.Quote
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.SignalLocalMetrics
import kotlin.math.max

class ConversationRepository(context: Context) {

  private val applicationContext = context.applicationContext
  private val oldConversationRepository = org.thoughtcrime.securesms.conversation.ConversationRepository()

  /**
   * Loads the details necessary to display the conversation thread.
   */
  fun getConversationThreadState(threadId: Long, requestedStartPosition: Int): Single<ConversationThreadState> {
    return Single.fromCallable {
      SignalLocalMetrics.ConversationOpen.onMetadataLoadStarted()
      val recipient = SignalDatabase.threads.getRecipientForThreadId(threadId)!!
      val metadata = oldConversationRepository.getConversationData(threadId, recipient, requestedStartPosition)
      val messageRequestData = metadata.messageRequestData
      val dataSource = ConversationDataSource(
        applicationContext,
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
      ).apply {
        SignalLocalMetrics.ConversationOpen.onMetadataLoaded()
      }
    }.subscribeOn(Schedulers.io())
  }

  /**
   * Generates the name color-map for groups.
   */
  fun getNameColorsMap(
    recipient: Recipient,
    groupAuthorNameColorHelper: GroupAuthorNameColorHelper
  ): Observable<Map<RecipientId, NameColor>> {
    return Recipient.observable(recipient.id)
      .distinctUntilChanged { a, b -> a.participantIds == b.participantIds }
      .map {
        if (it.groupId.isPresent) {
          groupAuthorNameColorHelper.getColorMap(it.requireGroupId())
        } else {
          emptyMap()
        }
      }
      .subscribeOn(Schedulers.io())
  }

  fun setLastVisibleMessageTimestamp(threadId: Long, lastVisibleMessageTimestamp: Long) {
    SignalExecutors.BOUNDED.submit { SignalDatabase.threads.setLastScrolled(threadId, lastVisibleMessageTimestamp) }
  }

  fun markGiftBadgeRevealed(messageId: Long) {
    oldConversationRepository.markGiftBadgeRevealed(messageId)
  }

  fun getQuotedMessagePosition(threadId: Long, quote: Quote): Single<Int> {
    return Single.fromCallable {
      SignalDatabase.messages.getQuotedMessagePosition(threadId, quote.id, quote.author)
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

  fun getMessageCounts(threadId: Long): Flowable<MessageCounts> {
    return RxDatabaseObserver.conversationList
      .map { getUnreadCount(threadId) }
      .distinctUntilChanged()
      .map { MessageCounts(it, getUnreadMentionsCount(threadId)) }
  }

  private fun getUnreadCount(threadId: Long): Int {
    val threadRecord = SignalDatabase.threads.getThreadRecord(threadId)
    return threadRecord?.unreadCount ?: 0
  }

  private fun getUnreadMentionsCount(threadId: Long): Int {
    return SignalDatabase.messages.getUnreadMentionCount(threadId)
  }

  data class MessageCounts(
    val unread: Int,
    val mentions: Int
  )
}
