package org.thoughtcrime.securesms.stories.viewer.reply.group

import android.content.Context
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.identity.IdentityRecordList
import org.thoughtcrime.securesms.database.model.Mention
import org.thoughtcrime.securesms.database.model.ParentStoryId
import org.thoughtcrime.securesms.database.model.StoryType
import org.thoughtcrime.securesms.mediasend.v2.UntrustedRecords
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage
import org.thoughtcrime.securesms.sms.MessageSender

/**
 * Stateless message sender for Story Group replies and reactions.
 */
object StoryGroupReplySender {

  fun sendReply(context: Context, storyId: Long, body: CharSequence, mentions: List<Mention>): Completable {
    return sendInternal(context, storyId, body, mentions, false)
  }

  fun sendReaction(context: Context, storyId: Long, emoji: String): Completable {
    return sendInternal(context, storyId, emoji, emptyList(), true)
  }

  private fun sendInternal(context: Context, storyId: Long, body: CharSequence, mentions: List<Mention>, isReaction: Boolean): Completable {
    val messageAndRecipient = Single.fromCallable {
      val message = SignalDatabase.mms.getMessageRecord(storyId)
      val recipient = SignalDatabase.threads.getRecipientForThreadId(message.threadId)!!

      message to recipient
    }

    return messageAndRecipient.flatMapCompletable { (message, recipient) ->
      UntrustedRecords.checkForBadIdentityRecords(setOf(ContactSearchKey.RecipientSearchKey.KnownRecipient(recipient.id)), System.currentTimeMillis() - IdentityRecordList.DEFAULT_UNTRUSTED_WINDOW)
        .andThen(
          Completable.create {
            MessageSender.send(
              context,
              OutgoingMediaMessage(
                recipient = recipient,
                body = body.toString(),
                timestamp = System.currentTimeMillis(),
                distributionType = 0,
                storyType = StoryType.NONE,
                parentStoryId = ParentStoryId.GroupReply(message.id),
                isStoryReaction = isReaction,
                mentions = mentions,
                isSecure = true
              ),
              message.threadId,
              false,
              null
            ) {
              it.onComplete()
            }
          }
        )
    }.subscribeOn(Schedulers.io())
  }
}
