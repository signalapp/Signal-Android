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
import org.thoughtcrime.securesms.database.DatabaseObserver
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.threads
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
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
    return Single.create { emitter ->
      val recipient = SignalDatabase.threads.getRecipientForThreadId(threadId)!!
      val metadata = oldConversationRepository.getConversationData(threadId, recipient, requestedStartPosition)
      val messageRequestData = metadata.messageRequestData
      val startPosition = when {
        metadata.shouldJumpToMessage() -> metadata.jumpToPosition
        messageRequestData.isMessageRequestAccepted && metadata.shouldScrollToLastSeen() -> metadata.lastSeenPosition
        messageRequestData.isMessageRequestAccepted -> metadata.lastScrolledPosition
        else -> metadata.threadSize
      }
      val dataSource = ConversationDataSource(
        applicationContext,
        threadId,
        messageRequestData,
        metadata.showUniversalExpireTimerMessage,
        metadata.threadSize
      )
      val config = PagingConfig.Builder().setPageSize(25)
        .setBufferPages(2)
        .setStartIndex(max(startPosition, 0))
        .build()

      val threadState = ConversationThreadState(
        items = PagedData.createForObservable(dataSource, config),
        meta = metadata
      )

      val controller = threadState.items.controller
      val messageUpdateObserver = DatabaseObserver.MessageObserver {
        controller.onDataItemChanged(it)
      }
      val messageInsertObserver = DatabaseObserver.MessageObserver {
        controller.onDataItemInserted(it, 0)
      }
      val conversationObserver = DatabaseObserver.Observer {
        controller.onDataInvalidated()
      }

      ApplicationDependencies.getDatabaseObserver().registerMessageUpdateObserver(messageUpdateObserver)
      ApplicationDependencies.getDatabaseObserver().registerMessageInsertObserver(threadId, messageInsertObserver)
      ApplicationDependencies.getDatabaseObserver().registerConversationObserver(threadId, conversationObserver)

      emitter.setCancellable {
        ApplicationDependencies.getDatabaseObserver().unregisterObserver(messageUpdateObserver)
        ApplicationDependencies.getDatabaseObserver().unregisterObserver(messageInsertObserver)
        ApplicationDependencies.getDatabaseObserver().unregisterObserver(conversationObserver)
      }

      emitter.onSuccess(threadState)
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
}
