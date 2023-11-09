package org.thoughtcrime.securesms.profiles.edit.pnp

import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import org.thoughtcrime.securesms.util.rx.RxStore

class WhoCanFindMeByPhoneNumberViewModel : ViewModel() {

  private val repository = WhoCanFindMeByPhoneNumberRepository()
  private val store = RxStore(repository.getCurrentState())
  private val disposables = CompositeDisposable()

  val state: Flowable<WhoCanFindMeByPhoneNumberState> = store.stateFlowable.subscribeOn(AndroidSchedulers.mainThread())

  fun onEveryoneCanFindMeByPhoneNumberSelected() {
    store.update {
      WhoCanFindMeByPhoneNumberState.EVERYONE
    }
  }

  fun onNobodyCanFindMeByPhoneNumberSelected() {
    store.update {
      WhoCanFindMeByPhoneNumberState.NOBODY
    }
  }

  fun onSave(): Completable {
    return repository.onSave(store.state).observeOn(AndroidSchedulers.mainThread())
  }

  override fun onCleared() {
    disposables.clear()
    store.dispose()
  }
}
