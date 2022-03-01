package org.thoughtcrime.securesms.stories.viewer.reply.direct

import android.content.Context
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.ParentStoryId
import org.thoughtcrime.securesms.database.model.StoryType
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage
import org.thoughtcrime.securesms.mms.QuoteModel
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.sms.MessageSender

class StoryDirectReplyRepository {

  fun getStoryPost(storyId: Long): Single<MessageRecord> {
    return Single.fromCallable {
      SignalDatabase.mms.getMessageRecord(storyId)
    }.subscribeOn(Schedulers.io())
  }

  fun send(context: Context, storyId: Long, groupDirectReplyRecipientId: RecipientId?, charSequence: CharSequence): Completable {
    return Completable.create { emitter ->
      val message = SignalDatabase.mms.getMessageRecord(storyId) as MediaMmsMessageRecord
      val (recipient, threadId) = if (groupDirectReplyRecipientId == null) {
        message.recipient to message.threadId
      } else {
        val resolved = Recipient.resolved(groupDirectReplyRecipientId)
        resolved to SignalDatabase.threads.getOrCreateThreadIdFor(resolved)
      }

      val quoteAuthor: Recipient = if (message.isOutgoing) {
        Recipient.self()
      } else {
        message.individualRecipient
      }

      if (!quoteAuthor.serviceId.isPresent || !quoteAuthor.e164.isPresent) {
        throw AssertionError("Bad quote author.")
      }

      MessageSender.send(
        context,
        OutgoingMediaMessage(
          recipient,
          charSequence.toString(),
          emptyList(),
          System.currentTimeMillis(),
          0,
          0L,
          false,
          0,
          StoryType.NONE,
          ParentStoryId.DirectReply(storyId),
          QuoteModel(message.dateSent, quoteAuthor.id, "", false, message.slideDeck.asAttachments(), null),
          emptyList(),
          emptyList(),
          emptyList(),
          emptySet(),
          emptySet()
        ),
        threadId,
        false,
        null
      ) {
        emitter.onComplete()
      }
    }.subscribeOn(Schedulers.io())
  }
}
