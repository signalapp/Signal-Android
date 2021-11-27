package org.thoughtcrime.securesms.reactions

import android.content.Context
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.ObservableEmitter
import io.reactivex.rxjava3.schedulers.Schedulers
import org.thoughtcrime.securesms.components.emoji.EmojiUtil
import org.thoughtcrime.securesms.database.DatabaseObserver
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.database.model.ReactionRecord
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.recipients.Recipient

class ReactionsRepository {

  fun getReactions(messageId: MessageId): Observable<List<ReactionDetails>> {
    return Observable.create { emitter: ObservableEmitter<List<ReactionDetails>> ->
      val databaseObserver: DatabaseObserver = ApplicationDependencies.getDatabaseObserver()

      val messageObserver = DatabaseObserver.MessageObserver { messageId ->
        emitter.onNext(fetchReactionDetails(messageId))
      }

      databaseObserver.registerMessageUpdateObserver(messageObserver)

      emitter.setCancellable {
        databaseObserver.unregisterObserver(messageObserver)
      }

      emitter.onNext(fetchReactionDetails(messageId))
    }.subscribeOn(Schedulers.io())
  }

  private fun fetchReactionDetails(messageId: MessageId): List<ReactionDetails> {
    val context: Context = ApplicationDependencies.getApplication()
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
}
