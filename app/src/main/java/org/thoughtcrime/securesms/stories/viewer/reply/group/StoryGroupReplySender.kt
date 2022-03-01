package org.thoughtcrime.securesms.stories.viewer.reply.group

import android.content.Context
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.Mention
import org.thoughtcrime.securesms.database.model.ParentStoryId
import org.thoughtcrime.securesms.database.model.StoryType
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage
import org.thoughtcrime.securesms.sms.MessageSender

/**
 * Stateless message sender for Story Group replies and reactions.
 */
object StoryGroupReplySender {
  fun sendReply(context: Context, storyId: Long, body: CharSequence, mentions: List<Mention>): Completable {
    return Completable.create {

      val message = SignalDatabase.mms.getMessageRecord(storyId)
      val recipient = SignalDatabase.threads.getRecipientForThreadId(message.threadId)!!

      MessageSender.send(
        context,
        OutgoingMediaMessage(
          recipient,
          body.toString(),
          emptyList(),
          System.currentTimeMillis(),
          0,
          0L,
          false,
          0,
          StoryType.NONE,
          ParentStoryId.GroupReply(message.id),
          null,
          emptyList(),
          emptyList(),
          mentions,
          emptySet(),
          emptySet()
        ),
        message.threadId,
        false,
        null
      ) {
        it.onComplete()
      }
    }.subscribeOn(Schedulers.io())
  }

  fun sendReaction(context: Context, storyId: Long, emoji: String): Completable {
    // TODO [stories]
    return Completable.complete()
  }
}
