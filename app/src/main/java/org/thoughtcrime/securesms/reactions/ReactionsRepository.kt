package org.thoughtcrime.securesms.reactions

import android.content.Context
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.ObservableEmitter
import io.reactivex.rxjava3.schedulers.Schedulers
import org.thoughtcrime.securesms.components.emoji.EmojiUtil
import org.thoughtcrime.securesms.database.DatabaseObserver
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.database.model.ReactionRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.sms.MessageSender
import java.util.NoSuchElementException

class ReactionsRepository {

  fun getReactions(messageId: MessageId): Observable<List<ReactionDetails>> {
    return Observable.create { emitter: ObservableEmitter<List<ReactionDetails>> ->
      val databaseObserver: DatabaseObserver = AppDependencies.databaseObserver

      val messageObserver = DatabaseObserver.MessageObserver { reactionMessageId ->
        if (reactionMessageId == messageId) {
          emitter.onNext(fetchReactionDetails(reactionMessageId))
        }
      }

      databaseObserver.registerMessageUpdateObserver(messageObserver)

      emitter.setCancellable {
        databaseObserver.unregisterObserver(messageObserver)
      }

      emitter.onNext(fetchReactionDetails(messageId))
    }.subscribeOn(Schedulers.io())
  }

  private fun fetchReactionDetails(messageId: MessageId): List<ReactionDetails> {
    val reactions: List<ReactionRecord> = SignalDatabase.reactions.getReactions(messageId)

    return reactions.map { reaction ->
      ReactionDetails(
        sender = Recipient.resolved(reaction.author),
        baseEmoji = EmojiUtil.getCanonicalRepresentation(reaction.emoji),
        displayEmoji = reaction.emoji,
        timestamp = reaction.dateReceived
      )
    }
  }

  fun sendReactionRemoval(context: Context, messageId: MessageId): Completable {
    val oldReactionRecord = oldReactionRecord(messageId) ?: return Completable.error(NoSuchElementException("Removing invalid emoji!"))
    return Completable.fromAction {
      MessageSender.sendReactionRemoval(
        context.applicationContext,
        MessageId(messageId.id),
        oldReactionRecord
      )
    }.subscribeOn(Schedulers.io())
  }

  private fun oldReactionRecord(messageId: MessageId): ReactionRecord? {
    return SignalDatabase.reactions.getReactions(messageId).firstOrNull { it.author == Recipient.self().id }
  }
}
