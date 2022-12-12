package org.thoughtcrime.securesms.keyboard.sticker

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil

class StickerSearchViewModel(private val searchRepository: StickerSearchRepository) : ViewModel() {

  private val searchQuery: MutableLiveData<String> = MutableLiveData("")

  val searchResults: LiveData<List<KeyboardStickerListAdapter.Sticker>> = LiveDataUtil.mapAsync(searchQuery) { q ->
    searchRepository.search(q)
      .map { KeyboardStickerListAdapter.Sticker(it.packId, it) }
  }

  fun query(query: String) {
    searchQuery.postValue(query)
  }

  class Factory : ViewModelProvider.Factory {
    val repository = StickerSearchRepository()

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return requireNotNull(modelClass.cast(StickerSearchViewModel(repository)))
    }
  }
}
