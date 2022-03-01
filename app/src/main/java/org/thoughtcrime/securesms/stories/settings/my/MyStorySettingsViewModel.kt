package org.thoughtcrime.securesms.stories.settings.my

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.thoughtcrime.securesms.util.livedata.Store

class MyStorySettingsViewModel(private val repository: MyStorySettingsRepository) : ViewModel() {
  private val store = Store(MyStorySettingsState())
  private val disposables = CompositeDisposable()

  val state: LiveData<MyStorySettingsState> = store.stateLiveData

  override fun onCleared() {
    disposables.clear()
  }

  fun refresh() {
    disposables.clear()
    disposables += repository.getHiddenRecipientCount()
      .subscribe { count -> store.update { it.copy(hiddenStoryFromCount = count) } }
    disposables += repository.getRepliesAndReactionsEnabled()
      .subscribe { repliesAndReactionsEnabled -> store.update { it.copy(areRepliesAndReactionsEnabled = repliesAndReactionsEnabled) } }
  }

  fun setRepliesAndReactionsEnabled(repliesAndReactionsEnabled: Boolean) {
    disposables += repository.setRepliesAndReactionsEnabled(repliesAndReactionsEnabled)
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe { refresh() }
  }

  class Factory(private val repository: MyStorySettingsRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
      return modelClass.cast(MyStorySettingsViewModel(repository)) as T
    }
  }
}
