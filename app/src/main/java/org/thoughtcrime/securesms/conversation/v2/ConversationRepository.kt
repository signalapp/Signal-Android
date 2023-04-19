package org.thoughtcrime.securesms.conversation.v2

import android.content.Context
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.paging.PagedData
import org.signal.paging.PagingConfig
import org.thoughtcrime.securesms.conversation.ConversationDataSource
import org.thoughtcrime.securesms.conversation.colors.GroupAuthorNameColorHelper
import org.thoughtcrime.securesms.conversation.colors.NameColor
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.threads
import org.thoughtcrime.securesms.database.model.Quote
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import kotlin.math.max

class ConversationRepository(context: Context) {

  private val applicationContext = context.applicationContext
  private val oldConversationRepository = org.thoughtcrime.securesms.conversation.ConversationRepository()

  /**
   * Observes the recipient tied to the given thread id, returning an error if
   * the thread id does not exist or somehow does not have a recipient attached to it.
   */
  fun observeRecipientForThread(threadId: Long): Observable<Recipient> {
    return Observable.create { emitter ->
      val recipientId = SignalDatabase.threads.getRecipientIdForThreadId(threadId)

      if (recipientId != null) {
        val disposable = Recipient.live(recipientId).observable()
          .subscribeOn(Schedulers.io())
          .subscribeBy(onNext = emitter::onNext)

        emitter.setCancellable {
          disposable.dispose()
        }
      } else {
        emitter.onError(Exception("Thread $threadId does not exist."))
      }
    }.subscribeOn(Schedulers.io())
  }

  /**
   * Loads the details necessary to display the conversation thread.
   */
  fun getConversationThreadState(threadId: Long, requestedStartPosition: Int): Single<ConversationThreadState> {
    return Single.fromCallable {
      val recipient = threads.getRecipientForThreadId(threadId)!!
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
      )
    }
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
    SignalExecutors.BOUNDED.submit { threads.setLastScrolled(threadId, lastVisibleMessageTimestamp) }
  }

  fun markGiftBadgeRevealed(messageId: Long) {
    oldConversationRepository.markGiftBadgeRevealed(messageId)
  }

  fun getQuotedMessagePosition(threadId: Long, quote: Quote): Single<Int> {
    return Single.fromCallable {
      SignalDatabase.messages.getQuotedMessagePosition(threadId, quote.id, quote.author)
    }.subscribeOn(Schedulers.io())
  }
}
