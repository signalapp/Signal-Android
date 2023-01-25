package org.thoughtcrime.securesms.conversation.quotes

import android.app.Application
import androidx.annotation.WorkerThread
import io.reactivex.rxjava3.core.Observable
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.conversation.ConversationDataSource
import org.thoughtcrime.securesms.conversation.ConversationMessage
import org.thoughtcrime.securesms.conversation.ConversationMessage.ConversationMessageFactory
import org.thoughtcrime.securesms.database.DatabaseObserver
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.Quote
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.util.getQuote

class MessageQuotesRepository {

  companion object {
    private val TAG = Log.tag(MessageQuotesRepository::class.java)
  }

  /**
   * Retrieves all messages that quote the target message, as well as any messages that quote _those_ messages, recursively.
   */
  fun getMessagesInQuoteChain(application: Application, messageId: MessageId): Observable<List<ConversationMessage>> {
    return Observable.create { emitter ->
      val threadId: Long = SignalDatabase.messages.getThreadIdForMessage(messageId.id)
      if (threadId < 0) {
        Log.w(TAG, "Could not find a threadId for $messageId!")
        emitter.onNext(emptyList())
        return@create
      }

      val databaseObserver: DatabaseObserver = ApplicationDependencies.getDatabaseObserver()
      val observer = DatabaseObserver.Observer { emitter.onNext(getMessagesInQuoteChainSync(application, messageId)) }

      databaseObserver.registerConversationObserver(threadId, observer)

      emitter.setCancellable { databaseObserver.unregisterObserver(observer) }
      emitter.onNext(getMessagesInQuoteChainSync(application, messageId))
    }
  }

  @WorkerThread
  private fun getMessagesInQuoteChainSync(application: Application, messageId: MessageId): List<ConversationMessage> {
    val rootMessageId: MessageId = SignalDatabase.messages.getRootOfQuoteChain(messageId)

    var originalRecord: MessageRecord? = SignalDatabase.messages.getMessageRecordOrNull(rootMessageId.id)

    if (originalRecord == null) {
      return emptyList()
    }

    var replyRecords: List<MessageRecord> = SignalDatabase.messages.getAllMessagesThatQuote(rootMessageId)

    val reactionHelper = ConversationDataSource.ReactionHelper()
    val attachmentHelper = ConversationDataSource.AttachmentHelper()

    reactionHelper.addAll(replyRecords)
    attachmentHelper.addAll(replyRecords)

    reactionHelper.fetchReactions()
    attachmentHelper.fetchAttachments()

    replyRecords = reactionHelper.buildUpdatedModels(replyRecords)
    replyRecords = attachmentHelper.buildUpdatedModels(ApplicationDependencies.getApplication(), replyRecords)

    val replies: List<ConversationMessage> = replyRecords
      .map { replyRecord ->
        val replyQuote: Quote? = replyRecord.getQuote()
        if (replyQuote != null && replyQuote.id == originalRecord!!.dateSent) {
          (replyRecord as MediaMmsMessageRecord).withoutQuote()
        } else {
          replyRecord
        }
      }
      .map { ConversationMessageFactory.createWithUnresolvedData(application, it) }

    if (originalRecord.isPaymentNotification) {
      originalRecord = SignalDatabase.payments.updateMessageWithPayment(originalRecord)
    }

    originalRecord = ConversationDataSource.ReactionHelper()
      .apply {
        add(originalRecord)
        fetchReactions()
      }
      .buildUpdatedModels(listOf(originalRecord))
      .get(0)

    originalRecord = ConversationDataSource.AttachmentHelper()
      .apply {
        add(originalRecord)
        fetchAttachments()
      }
      .buildUpdatedModels(ApplicationDependencies.getApplication(), listOf(originalRecord))
      .get(0)

    val originalMessage: ConversationMessage = ConversationMessageFactory.createWithUnresolvedData(application, originalRecord, false)

    return replies + originalMessage
  }
}
