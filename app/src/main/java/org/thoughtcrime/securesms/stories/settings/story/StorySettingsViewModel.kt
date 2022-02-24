package org.thoughtcrime.securesms.stories.settings.story

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.thoughtcrime.securesms.util.livedata.Store

class StorySettingsViewModel(
  private val repository: StorySettingsRepository
) : ViewModel() {

  private val store = Store(StorySettingsState())
  private val disposables = CompositeDisposable()

  val state: LiveData<StorySettingsState> = store.stateLiveData

  fun refresh() {
    disposables += repository.getPrivateStories().subscribe { privateStories ->
      store.update { it.copy(privateStories = privateStories) }
    }
  }

  override fun onCleared() {
    disposables.clear()
  }

  class Factory(
    private val repository: StorySettingsRepository
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
      return modelClass.cast(StorySettingsViewModel(repository)) as T
    }
  }
}
