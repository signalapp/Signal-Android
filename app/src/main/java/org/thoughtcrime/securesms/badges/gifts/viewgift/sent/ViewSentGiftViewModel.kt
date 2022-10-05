package org.thoughtcrime.securesms.badges.gifts.viewgift.sent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.thoughtcrime.securesms.badges.gifts.viewgift.ViewGiftRepository
import org.thoughtcrime.securesms.database.model.databaseprotos.GiftBadge
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.rx.RxStore

class ViewSentGiftViewModel(
  sentFrom: RecipientId,
  giftBadge: GiftBadge,
  repository: ViewGiftRepository
) : ViewModel() {

  private val store = RxStore(ViewSentGiftState())
  private val disposables = CompositeDisposable()

  val state: Flowable<ViewSentGiftState> = store.stateFlowable

  init {
    disposables += Recipient.observable(sentFrom).subscribe { recipient ->
      store.update { it.copy(recipient = recipient) }
    }

    disposables += repository.getBadge(giftBadge).subscribe { badge ->
      store.update {
        it.copy(
          badge = badge
        )
      }
    }
  }

  override fun onCleared() {
    disposables.dispose()
    store.dispose()
  }

  class Factory(
    private val sentFrom: RecipientId,
    private val giftBadge: GiftBadge,
    private val repository: ViewGiftRepository
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(ViewSentGiftViewModel(sentFrom, giftBadge, repository)) as T
    }
  }
}
