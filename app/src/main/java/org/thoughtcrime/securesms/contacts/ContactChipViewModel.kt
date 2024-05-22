package org.thoughtcrime.securesms.contacts

import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.schedulers.Schedulers
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.rx.RxStore

/**
 * ViewModel expressly for displaying the current state of the contact chips
 * in the contact selection fragment.
 */
class ContactChipViewModel : ViewModel() {

  private val store = RxStore(emptyList<SelectedContacts.Model>())

  val state: Flowable<List<SelectedContacts.Model>> = store.stateFlowable
    .distinctUntilChanged()
    .observeOn(AndroidSchedulers.mainThread())

  val count = store.state.size

  private val disposables = CompositeDisposable()
  private val disposableMap: MutableMap<RecipientId, Disposable> = mutableMapOf()

  override fun onCleared() {
    disposables.clear()
    disposableMap.values.forEach { it.dispose() }
    disposableMap.clear()
    store.dispose()
  }

  fun add(selectedContact: SelectedContact) {
    disposables += getOrCreateRecipientId(selectedContact).map { Recipient.resolved(it) }.observeOn(Schedulers.io()).subscribe { recipient ->
      store.update { it + SelectedContacts.Model(selectedContact, recipient) }
      disposableMap[recipient.id]?.dispose()
      disposableMap[recipient.id] = store.update(recipient.live().observable().toFlowable(BackpressureStrategy.LATEST)) { changedRecipient, state ->
        val index = state.indexOfFirst { it.selectedContact.matches(selectedContact) }
        when {
          index == 0 -> {
            listOf(SelectedContacts.Model(selectedContact, changedRecipient)) + state.drop(index + 1)
          }
          index > 0 -> {
            state.take(index) + SelectedContacts.Model(selectedContact, changedRecipient) + state.drop(index + 1)
          }
          else -> {
            state
          }
        }
      }
    }
  }

  fun remove(selectedContact: SelectedContact) {
    store.update { list ->
      list.filterNot { it.selectedContact.matches(selectedContact) }
    }
  }

  private fun getOrCreateRecipientId(selectedContact: SelectedContact): Single<RecipientId> {
    return Single.fromCallable {
      selectedContact.getOrCreateRecipientId(AppDependencies.application)
    }
  }
}
