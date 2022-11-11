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
      val threadId: Long = SignalDatabase.mmsSms.getThreadId(messageId)
      if (threadId < 0) {
        Log.w(TAG, "Could not find a threadId for $messageId!")
        emitter.onNext(emptyList())
        return@create
      }

      val databaseObserver: DatabaseObserver = ApplicationDependencies.getDatabaseObserver()
      val observer = DatabaseObserver.Observer { emitter.onNext(getMessageInQuoteChainSync(application, messageId)) }

      databaseObserver.registerConversationObserver(threadId, observer)

      emitter.setCancellable { databaseObserver.unregisterObserver(observer) }
      emitter.onNext(getMessageInQuoteChainSync(application, messageId))
    }
  }

  @WorkerThread
  private fun getMessageInQuoteChainSync(application: Application, messageId: MessageId): List<ConversationMessage> {
    var originalRecord: MessageRecord? = if (messageId.mms) {
      SignalDatabase.mms.getMessageRecordOrNull(messageId.id)
    } else {
      SignalDatabase.sms.getMessageRecordOrNull(messageId.id)
    }

    if (originalRecord == null) {
      return emptyList()
    }

    val replyRecords: List<MessageRecord> = SignalDatabase.mmsSms.getAllMessagesThatQuote(messageId)

    val replies: List<ConversationMessage> = ConversationDataSource.ReactionHelper()
      .apply {
        addAll(replyRecords)
        fetchReactions()
      }
      .buildUpdatedModels(replyRecords)
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

    val originalMessage: List<ConversationMessage> = ConversationDataSource.ReactionHelper()
      .apply {
        add(originalRecord)
        fetchReactions()
      }
      .buildUpdatedModels(listOf(originalRecord))
      .map { ConversationMessageFactory.createWithUnresolvedData(application, it, it.getDisplayBody(application), false) }

    return replies + originalMessage
  }
}
