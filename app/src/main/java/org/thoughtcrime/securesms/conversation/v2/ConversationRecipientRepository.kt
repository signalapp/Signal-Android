package org.thoughtcrime.securesms.conversation.v2

import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.schedulers.Schedulers
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.GroupRecord
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import java.util.Optional

class ConversationRecipientRepository(threadId: Long, val recipientId: RecipientId) : ViewModel() {

  private val disposables = CompositeDisposable()

  val conversationRecipient: Observable<Recipient> by lazy {
    Recipient.observable(recipientId)
      .replay(1)
      .refCount()
      .observeOn(Schedulers.io())
  }

  val groupRecord: Observable<Optional<GroupRecord>> by lazy {
    conversationRecipient
      .switchMapSingle {
        Single.fromCallable {
          if (it.isGroup) {
            SignalDatabase.groups.getGroup(it.id)
          } else {
            Optional.empty()
          }
        }
      }
      .replay(1)
      .refCount()
      .observeOn(Schedulers.io())
  }

  init {
    disposables += Completable
      .fromAction {
        val threadRecipientId = SignalDatabase.threads.getRecipientIdForThreadId(threadId)
        val resolvedRecipientId = Recipient.resolved(recipientId).id
        check(threadRecipientId == resolvedRecipientId) {
          "Thread $threadId belongs to $threadRecipientId, but the conversation was opened for $recipientId (resolved to $resolvedRecipientId)."
        }
      }
      .subscribeOn(Schedulers.io())
      .subscribe()
  }

  override fun onCleared() {
    disposables.clear()
  }
}
