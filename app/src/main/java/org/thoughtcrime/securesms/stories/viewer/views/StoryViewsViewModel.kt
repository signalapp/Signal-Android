package org.thoughtcrime.securesms.stories.viewer.views

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.thoughtcrime.securesms.util.livedata.Store

class StoryViewsViewModel(storyId: Long, repository: StoryViewsRepository) : ViewModel() {

  private val store = Store(StoryViewsState())
  private val disposables = CompositeDisposable()

  val state: LiveData<StoryViewsState> = store.stateLiveData

  init {
    disposables += repository.getViews(storyId).subscribe { data ->
      store.update {
        it.copy(
          views = data,
          loadState = StoryViewsState.LoadState.READY
        )
      }
    }
  }

  override fun onCleared() {
    disposables.clear()
  }

  class Factory(
    private val storyId: Long,
    private val repository: StoryViewsRepository
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
      return modelClass.cast(StoryViewsViewModel(storyId, repository)) as T
    }
  }
}
