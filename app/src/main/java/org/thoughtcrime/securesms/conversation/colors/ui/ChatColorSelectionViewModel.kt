package org.thoughtcrime.securesms.conversation.colors.ui

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.thoughtcrime.securesms.conversation.colors.ChatColors
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.SingleLiveEvent
import org.thoughtcrime.securesms.util.livedata.Store

class ChatColorSelectionViewModel(private val repository: ChatColorSelectionRepository) : ViewModel() {

  private val store = Store<ChatColorSelectionState>(ChatColorSelectionState())
  private val chatColors = ChatColorsOptionsLiveData()
  private val internalEvents = SingleLiveEvent<Event>()

  val state: LiveData<ChatColorSelectionState> = store.stateLiveData
  val events: LiveData<Event> = internalEvents

  init {
    store.update(chatColors) { colors, state -> state.copy(chatColorOptions = colors) }
  }

  fun refresh() {
    repository.getWallpaper { wallpaper ->
      store.update { it.copy(wallpaper = wallpaper) }
    }

    repository.getChatColors { chatColors ->
      store.update { it.copy(chatColors = chatColors) }
    }
  }

  fun save(chatColors: ChatColors) {
    repository.save(chatColors, this::refresh)
  }

  fun duplicate(chatColors: ChatColors) {
    repository.duplicate(chatColors)
  }

  fun startDeletion(chatColors: ChatColors) {
    repository.getUsageCount(chatColors.id) {
      internalEvents.postValue(Event.ConfirmDeletion(it, chatColors))
    }
  }

  fun deleteNow(chatColors: ChatColors) {
    repository.delete(chatColors, this::refresh)
  }

  class Factory(private val repository: ChatColorSelectionRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T = requireNotNull(modelClass.cast(ChatColorSelectionViewModel(repository)))
  }

  companion object {
    fun getOrCreate(activity: FragmentActivity, recipientId: RecipientId?): ChatColorSelectionViewModel {
      val repository = ChatColorSelectionRepository.create(activity, recipientId)
      val viewModelFactory = Factory(repository)

      return ViewModelProvider(activity, viewModelFactory).get(ChatColorSelectionViewModel::class.java)
    }
  }

  sealed class Event {
    class ConfirmDeletion(val usageCount: Int, val chatColors: ChatColors) : Event()
  }
}
