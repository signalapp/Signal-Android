package org.thoughtcrime.securesms.stories.tabs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject
import org.thoughtcrime.securesms.stories.Stories
import org.thoughtcrime.securesms.util.rx.RxStore

class ConversationListTabsViewModel(startingTab: ConversationListTab, repository: ConversationListTabRepository) : ViewModel() {
  private val store = RxStore(ConversationListTabsState(tab = startingTab))

  val stateSnapshot: ConversationListTabsState
    get() = store.state

  val state: Flowable<ConversationListTabsState> = store.stateFlowable.distinctUntilChanged().observeOn(AndroidSchedulers.mainThread())
  val disposables = CompositeDisposable()

  private val internalTabClickEvents: Subject<ConversationListTab> = PublishSubject.create()
  val tabClickEvents: Observable<ConversationListTab> = internalTabClickEvents.filter { Stories.isFeatureEnabled() }

  init {
    disposables += performStoreUpdate(repository.getNumberOfUnreadMessages()) { unreadChats, state ->
      state.copy(unreadMessagesCount = unreadChats)
    }

    disposables += performStoreUpdate(repository.getNumberOfUnseenCalls()) { unseenCalls, state ->
      state.copy(unreadCallsCount = unseenCalls)
    }

    disposables += performStoreUpdate(repository.getNumberOfUnseenStories()) { unseenStories, state ->
      state.copy(unreadStoriesCount = unseenStories)
    }

    disposables += performStoreUpdate(repository.getHasFailedOutgoingStories()) { hasFailedStories, state ->
      state.copy(hasFailedStory = hasFailedStories)
    }
  }

  override fun onCleared() {
    disposables.clear()
  }

  fun onChatsSelected() {
    internalTabClickEvents.onNext(ConversationListTab.CHATS)
    performStoreUpdate { it.copy(tab = ConversationListTab.CHATS) }
  }

  fun onCallsSelected() {
    internalTabClickEvents.onNext(ConversationListTab.CALLS)
    performStoreUpdate { it.copy(tab = ConversationListTab.CALLS) }
  }

  fun onStoriesSelected() {
    internalTabClickEvents.onNext(ConversationListTab.STORIES)
    performStoreUpdate { it.copy(tab = ConversationListTab.STORIES) }
  }

  fun onSearchOpened() {
    performStoreUpdate { it.copy(visibilityState = it.visibilityState.copy(isSearchOpen = true)) }
  }

  fun onSearchClosed() {
    performStoreUpdate { it.copy(visibilityState = it.visibilityState.copy(isSearchOpen = false)) }
  }

  fun onMultiSelectStarted() {
    performStoreUpdate { it.copy(visibilityState = it.visibilityState.copy(isMultiSelectOpen = true)) }
  }

  fun onMultiSelectFinished() {
    performStoreUpdate { it.copy(visibilityState = it.visibilityState.copy(isMultiSelectOpen = false)) }
  }

  fun isShowingArchived(isShowingArchived: Boolean) {
    performStoreUpdate { it.copy(visibilityState = it.visibilityState.copy(isShowingArchived = isShowingArchived)) }
  }

  private fun performStoreUpdate(fn: (ConversationListTabsState) -> ConversationListTabsState) {
    store.update {
      fn(it.copy(prevTab = it.tab))
    }
  }

  private fun <T : Any> performStoreUpdate(flowable: Flowable<T>, fn: (T, ConversationListTabsState) -> ConversationListTabsState): Disposable {
    return store.update(flowable) { t, state ->
      fn(t, state.copy(prevTab = state.tab))
    }
  }

  class Factory(private val startingTab: ConversationListTab?, private val repository: ConversationListTabRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      val tab = if (startingTab == null || (startingTab == ConversationListTab.STORIES && !Stories.isFeatureEnabled())) {
        ConversationListTab.CHATS
      } else {
        startingTab
      }
      return modelClass.cast(ConversationListTabsViewModel(tab, repository)) as T
    }
  }
}
