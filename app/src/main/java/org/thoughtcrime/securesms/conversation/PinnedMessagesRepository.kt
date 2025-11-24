package org.thoughtcrime.securesms.conversation

import android.app.Application
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.conversation.ConversationMessage.ConversationMessageFactory
import org.thoughtcrime.securesms.conversation.v2.data.AttachmentHelper
import org.thoughtcrime.securesms.conversation.v2.data.ReactionHelper
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies

/**
 * Repository when getting the pinned messages shown in the pinned message bottom sheet
 */
class PinnedMessagesRepository {

  companion object {
    private val TAG = Log.tag(PinnedMessagesRepository::class.java)
  }

  fun getPinnedMessage(application: Application, threadId: Long): Observable<List<ConversationMessage>> {
    return Observable.create { emitter ->
      emitter.onNext(getPinnedMessages(application, threadId))
    }.subscribeOn(Schedulers.io())
  }

  fun getPinnedMessageRecords(threadId: Long): List<MessageRecord> {
    return SignalDatabase.messages.getPinnedMessages(threadId = threadId, orderByPinned = false)
  }

  private fun getPinnedMessages(application: Application, threadId: Long): List<ConversationMessage> {
    var records: List<MessageRecord> = getPinnedMessageRecords(threadId)

    val reactionHelper = ReactionHelper()
    val attachmentHelper = AttachmentHelper()
    val threadRecipient = requireNotNull(SignalDatabase.threads.getRecipientForThreadId(threadId))

    reactionHelper.addAll(records)
    attachmentHelper.addAll(records)

    reactionHelper.fetchReactions()
    attachmentHelper.fetchAttachments()

    records = reactionHelper.buildUpdatedModels(records)
    records = attachmentHelper.buildUpdatedModels(AppDependencies.application, records)

    return records.map { ConversationMessageFactory.createWithUnresolvedData(application, it, threadRecipient) }
  }
}
