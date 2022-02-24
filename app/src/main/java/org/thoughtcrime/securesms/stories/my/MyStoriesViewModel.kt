package org.thoughtcrime.securesms.stories.my

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.thoughtcrime.securesms.util.livedata.Store

class MyStoriesViewModel(private val repository: MyStoriesRepository) : ViewModel() {

  private val store = Store(MyStoriesState())
  private val disposables = CompositeDisposable()

  val state: LiveData<MyStoriesState> = store.stateLiveData

  init {
    disposables += repository.getMyStories().subscribe { distributionSets ->
      store.update { it.copy(distributionSets = distributionSets) }
    }
  }

  override fun onCleared() {
    disposables.clear()
  }

  class Factory(private val repository: MyStoriesRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
      return modelClass.cast(MyStoriesViewModel(repository)) as T
    }
  }
}
