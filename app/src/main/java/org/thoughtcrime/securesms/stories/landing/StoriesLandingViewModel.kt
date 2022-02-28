package org.thoughtcrime.securesms.stories.landing

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.livedata.Store

class StoriesLandingViewModel(private val storiesLandingRepository: StoriesLandingRepository) : ViewModel() {
  private val store = Store(StoriesLandingState())
  private val disposables = CompositeDisposable()

  val state: LiveData<StoriesLandingState> = store.stateLiveData

  init {
    disposables += storiesLandingRepository.getStories().subscribe { stories ->
      store.update { state ->
        state.copy(
          loadingState = StoriesLandingState.LoadingState.LOADED,
          storiesLandingItems = stories.sorted(),
          displayMyStoryItem = stories.isEmpty() || stories.none { it.storyRecipient.isMyStory }
        )
      }
    }
  }

  override fun onCleared() {
    disposables.clear()
  }

  fun setHideStory(sender: Recipient, hide: Boolean): Completable {
    return storiesLandingRepository.setHideStory(sender.id, hide)
  }

  fun setHiddenContentVisible(isExpanded: Boolean) {
    store.update { it.copy(isHiddenContentVisible = isExpanded) }
  }

  class Factory(private val storiesLandingRepository: StoriesLandingRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
      return modelClass.cast(StoriesLandingViewModel(storiesLandingRepository)) as T
    }
  }
}
