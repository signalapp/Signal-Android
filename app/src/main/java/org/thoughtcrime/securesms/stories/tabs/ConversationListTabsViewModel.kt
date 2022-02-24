package org.thoughtcrime.securesms.stories.tabs

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.thoughtcrime.securesms.util.livedata.Store

class ConversationListTabsViewModel(repository: ConversationListTabRepository) : ViewModel() {
  private val store = Store(ConversationListTabsState())

  val state: LiveData<ConversationListTabsState> = store.stateLiveData
  val disposables = CompositeDisposable()

  init {
    disposables += repository.getNumberOfUnreadConversations().subscribe { unreadChats ->
      store.update { it.copy(unreadChatsCount = unreadChats) }
    }

    disposables += repository.getNumberOfUnseenStories().subscribe { unseenStories ->
      store.update { it.copy(unreadStoriesCount = unseenStories) }
    }
  }

  override fun onCleared() {
    disposables.clear()
  }

  fun onChatsSelected() {
    store.update { it.copy(tab = ConversationListTab.CHATS) }
  }

  fun onStoriesSelected() {
    store.update { it.copy(tab = ConversationListTab.STORIES) }
  }

  class Factory(private val repository: ConversationListTabRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
      return modelClass.cast(ConversationListTabsViewModel(repository)) as T
    }
  }
}
