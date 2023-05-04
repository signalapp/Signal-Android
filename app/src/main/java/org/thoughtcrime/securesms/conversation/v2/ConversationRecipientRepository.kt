package org.thoughtcrime.securesms.conversation.v2

import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.recipients.Recipient

class ConversationRecipientRepository(threadId: Long) {

  val conversationRecipient: Observable<Recipient> by lazy {
    val threadRecipientId = Single.fromCallable {
      SignalDatabase.threads.getRecipientIdForThreadId(threadId)!!
    }

    threadRecipientId
      .flatMapObservable { Recipient.observable(it) }
      .subscribeOn(Schedulers.io())
      .observeOn(Schedulers.io())
      .replay(1)
      .refCount()
      .observeOn(Schedulers.io())
  }
}
