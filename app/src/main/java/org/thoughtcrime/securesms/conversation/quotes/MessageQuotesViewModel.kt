package org.thoughtcrime.securesms.conversation.quotes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.thoughtcrime.securesms.conversation.ConversationDataSource
import org.thoughtcrime.securesms.conversation.ConversationMessage
import org.thoughtcrime.securesms.conversation.colors.GroupAuthorNameColorHelper
import org.thoughtcrime.securesms.conversation.colors.NameColor
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

class MessageQuotesViewModel(
  application: Application,
  private val messageId: MessageId,
  private val conversationRecipientId: RecipientId
) : AndroidViewModel(application) {

  private val groupAuthorNameColorHelper = GroupAuthorNameColorHelper()

  fun getMessages(): Observable<List<ConversationMessage>> {
    return Observable.create<List<ConversationMessage>> { emitter ->
      val records: List<MessageRecord> = SignalDatabase.mmsSms.getAllMessagesThatQuote(messageId)

      val helper = ConversationDataSource.ReactionHelper()
      helper.addAll(records)
      helper.fetchReactions()

      val quotes = helper.buildUpdatedModels(records)
        .map { ConversationMessage.ConversationMessageFactory.createWithUnresolvedData(getApplication(), it) }

      val originalRecord: MessageRecord? = if (messageId.mms) {
        SignalDatabase.mms.getMessageRecordOrNull(messageId.id)
      } else {
        SignalDatabase.sms.getMessageRecordOrNull(messageId.id)
      }

      if (originalRecord != null) {
        val originalMessage: ConversationMessage = ConversationMessage.ConversationMessageFactory.createWithUnresolvedData(getApplication(), originalRecord, originalRecord.getDisplayBody(getApplication()), 0)
        emitter.onNext(quotes + listOf(originalMessage))
      } else {
        emitter.onNext(quotes)
      }
    }
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
  }

  fun getNameColorsMap(): Observable<Map<RecipientId, NameColor>> {
    return Observable.just(conversationRecipientId)
      .map { conversationRecipientId ->
        val conversationRecipient = Recipient.resolved(conversationRecipientId)

        if (conversationRecipient.groupId.isPresent) {
          groupAuthorNameColorHelper.getColorMap(conversationRecipient.groupId.get())
        } else {
          emptyMap()
        }
      }
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
  }

  class Factory(private val application: Application, private val messageId: MessageId, private val conversationRecipientId: RecipientId) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(MessageQuotesViewModel(application, messageId, conversationRecipientId)) as T
    }
  }
}
