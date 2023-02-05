package org.thoughtcrime.securesms.stories.settings.my

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.schedulers.Schedulers
import org.thoughtcrime.securesms.database.model.DistributionListPrivacyMode
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.stories.Stories
import org.thoughtcrime.securesms.util.livedata.Store

class MyStorySettingsViewModel @JvmOverloads constructor(private val repository: MyStorySettingsRepository = MyStorySettingsRepository()) : ViewModel() {
  private val store = Store(MyStorySettingsState(hasUserPerformedManualSelection = SignalStore.storyValues().userHasBeenNotifiedAboutStories))
  private val disposables = CompositeDisposable()

  val state: LiveData<MyStorySettingsState> = store.stateLiveData

  override fun onCleared() {
    disposables.clear()
  }

  fun refresh() {
    disposables.clear()
    disposables += repository.getPrivacyState()
      .subscribe { myStoryPrivacyState -> store.update { it.copy(myStoryPrivacyState = myStoryPrivacyState) } }
    disposables += repository.getRepliesAndReactionsEnabled()
      .subscribe { repliesAndReactionsEnabled -> store.update { it.copy(areRepliesAndReactionsEnabled = repliesAndReactionsEnabled) } }
    disposables += repository.getAllSignalConnectionsCount()
      .subscribe { allSignalConnectionsCount -> store.update { it.copy(allSignalConnectionsCount = allSignalConnectionsCount) } }
  }

  fun setRepliesAndReactionsEnabled(repliesAndReactionsEnabled: Boolean) {
    disposables += repository.setRepliesAndReactionsEnabled(repliesAndReactionsEnabled)
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe { refresh() }
  }

  fun setMyStoryPrivacyMode(privacyMode: DistributionListPrivacyMode): Completable {
    store.update { state ->
      state.copy(hasUserPerformedManualSelection = true)
    }

    SignalStore.storyValues().userHasBeenNotifiedAboutStories = true

    return if (privacyMode == state.value!!.myStoryPrivacyState.privacyMode) {
      Completable.fromAction {
        Stories.onStorySettingsChanged(Recipient.self().id)
      }.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
    } else {
      repository.setPrivacyMode(privacyMode)
        .observeOn(AndroidSchedulers.mainThread())
        .doOnComplete { refresh() }
    }
  }
}
