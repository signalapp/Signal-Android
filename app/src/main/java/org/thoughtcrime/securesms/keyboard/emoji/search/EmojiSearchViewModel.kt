package org.thoughtcrime.securesms.keyboard.emoji.search

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.thoughtcrime.securesms.components.emoji.EmojiPageModel

class EmojiSearchViewModel(private val repository: EmojiSearchRepository) : ViewModel() {

  private val internalPageModel = MutableLiveData<EmojiPageModel>()

  val pageModel: LiveData<EmojiPageModel> = internalPageModel

  init {
    onQueryChanged("")
  }

  fun onQueryChanged(query: String) {
    repository.submitQuery(query, internalPageModel::postValue)
  }

  class Factory(private val repository: EmojiSearchRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
      return requireNotNull(modelClass.cast(EmojiSearchViewModel(repository)))
    }
  }
}
