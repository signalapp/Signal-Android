package org.thoughtcrime.securesms.keyboard.sticker

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.keyboard.sticker.KeyboardStickerPackListAdapter.StickerPack
import org.thoughtcrime.securesms.util.MappingModelList
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil

private const val NO_SELECTED_PAGE = "no_selected_page"

class StickerKeyboardPageViewModel(private val repository: StickerKeyboardRepository) : ViewModel() {
  private val keyboardStickerPacks: MutableLiveData<List<StickerKeyboardRepository.KeyboardStickerPack>> = MutableLiveData()

  private val selectedPack: MutableLiveData<String> = MutableLiveData(NO_SELECTED_PAGE)

  val packs: LiveData<List<StickerPack>>
  val stickers: LiveData<MappingModelList>

  init {
    val stickerPacks: LiveData<List<StickerPack>> = LiveDataUtil.mapAsync(keyboardStickerPacks) { packs ->
      packs.map { StickerPack(it) }
    }

    packs = LiveDataUtil.combineLatest(selectedPack, stickerPacks) { selected, packs ->
      if (packs.isEmpty()) {
        packs
      } else {
        val actualSelected = if (selected == NO_SELECTED_PAGE) packs[0].packRecord.packId else selected
        packs.map { it.copy(selected = it.packRecord.packId == actualSelected) }
      }
    }

    stickers = LiveDataUtil.mapAsync(keyboardStickerPacks) { packs ->
      val list = MappingModelList()

      packs.forEach { pack ->
        list += KeyboardStickerListAdapter.StickerHeader(pack.packId, pack.title, pack.titleResource)
        list += pack.stickers.map { KeyboardStickerListAdapter.Sticker(pack.packId, it) }
      }

      list
    }
  }

  fun getSelectedPack(): LiveData<String> = selectedPack

  fun selectPack(packId: String) {
    selectedPack.value = packId
  }

  fun refreshStickers() {
    repository.getStickerPacks { keyboardStickerPacks.postValue(it) }
  }

  class Factory(context: Context) : ViewModelProvider.Factory {
    private val repository = StickerKeyboardRepository(SignalDatabase.stickers)

    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
      return requireNotNull(modelClass.cast(StickerKeyboardPageViewModel(repository)))
    }
  }
}
