package org.thoughtcrime.securesms.reactions

import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import io.reactivex.schedulers.Schedulers
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.components.emoji.EmojiUtil
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.database.model.ReactionRecord
import org.thoughtcrime.securesms.dependencies.DatabaseComponent

class ReactionsRepository {

    fun getReactions(messageId: MessageId): Observable<List<ReactionDetails>> {
        return Observable.create { emitter: ObservableEmitter<List<ReactionDetails>> ->
            emitter.onNext(fetchReactionDetails(messageId))
        }.subscribeOn(Schedulers.io())
    }

    private fun fetchReactionDetails(messageId: MessageId): List<ReactionDetails> {
        val context = MessagingModuleConfiguration.shared.context
        val reactions: List<ReactionRecord> = DatabaseComponent.get(context).reactionDatabase().getReactions(messageId)

        return reactions.map { reaction ->
            ReactionDetails(
                sender = Recipient.from(context, Address.fromSerialized(reaction.author), false),
                baseEmoji = EmojiUtil.getCanonicalRepresentation(reaction.emoji),
                displayEmoji = reaction.emoji,
                timestamp = reaction.dateReceived,
                serverId = reaction.serverId,
                localId = reaction.messageId,
                isMms = reaction.isMms,
                count = reaction.count.toInt()
            )
        }
    }
}
