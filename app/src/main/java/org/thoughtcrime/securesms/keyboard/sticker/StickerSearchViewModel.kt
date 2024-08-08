package org.thoughtcrime.securesms.keyboard.sticker

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.StickerTable.StickerRecordReader
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModelList
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil

class StickerSearchViewModel(private val searchRepository: StickerSearchRepository) : ViewModel() {

  private val searchQuery: MutableLiveData<String> = MutableLiveData("")

  val searchResults: LiveData<MappingModelList> = LiveDataUtil.mapAsync(searchQuery) { q ->
    val results = searchRepository.search(q)
    val list = MappingModelList()
    var needsPackSeparator = false
    val stickerTable = SignalDatabase.stickers
    for (sticker in results) {
      if (sticker.isCover) {
        val pack = stickerTable.getStickerPack(sticker.packId)
        if (pack != null && pack.isInstalled) {
          list += KeyboardStickerListAdapter.StickerHeader(pack.packId, pack.title.orElse(null), null)
          StickerRecordReader(stickerTable.getStickersForPack(pack.packId)).use {
            var next = it.next
            while (next != null) {
              list += KeyboardStickerListAdapter.Sticker(pack.packId, next)
              next = it.next
            }
          }
          needsPackSeparator = true
        }
      } else {
        if (needsPackSeparator) {
          list += KeyboardStickerListAdapter.Separator()
          needsPackSeparator = false
        }
        list += KeyboardStickerListAdapter.Sticker(sticker.packId, sticker)
      }
    }

    list
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
