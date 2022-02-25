package org.thoughtcrime.securesms.stories.viewer

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.livedata.Store

class StoryViewerViewModel(
  private val startRecipientId: RecipientId,
  private val repository: StoryViewerRepository
) : ViewModel() {

  private val store = Store(StoryViewerState())
  private val disposables = CompositeDisposable()

  val state: LiveData<StoryViewerState> = store.stateLiveData

  private val scrollStatePublisher: MutableLiveData<Boolean> = MutableLiveData(false)
  val isScrolling: LiveData<Boolean> = scrollStatePublisher

  init {
    refresh()
  }

  fun setIsScrolling(isScrolling: Boolean) {
    scrollStatePublisher.value = isScrolling
  }

  private fun refresh() {
    disposables.clear()
    disposables += repository.getStories().subscribe { recipientIds ->
      store.update {
        val page: Int = if (it.pages.isNotEmpty()) {
          val oldPage = it.page
          val oldRecipient = it.pages[oldPage]

          val newPage = recipientIds.indexOf(oldRecipient)
          if (newPage == -1) {
            it.page
          } else {
            newPage
          }
        } else {
          it.page
        }
        updatePages(it.copy(pages = recipientIds), page)
      }
    }
  }

  override fun onCleared() {
    disposables.clear()
  }

  fun setSelectedPage(page: Int) {
    store.update {
      updatePages(it, page)
    }
  }

  fun onFinishedPosts(recipientId: RecipientId) {
    store.update {
      if (it.pages[it.page] == recipientId) {
        updatePages(it, it.page + 1)
      } else {
        it
      }
    }
  }

  fun onRecipientHidden() {
    refresh()
  }

  private fun updatePages(state: StoryViewerState, page: Int): StoryViewerState {
    val newPage = resolvePage(page, state.pages)
    val prevPage = if (newPage == state.page) {
      state.previousPage
    } else {
      state.page
    }

    return state.copy(
      page = newPage,
      previousPage = prevPage
    )
  }

  private fun resolvePage(page: Int, recipientIds: List<RecipientId>): Int {
    return if (page > -1) {
      page
    } else {
      val indexOfStartRecipient = recipientIds.indexOf(startRecipientId)
      if (indexOfStartRecipient == -1) {
        0
      } else {
        indexOfStartRecipient
      }
    }
  }

  class Factory(
    private val startRecipientId: RecipientId,
    private val repository: StoryViewerRepository
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
      return modelClass.cast(StoryViewerViewModel(startRecipientId, repository)) as T
    }
  }
}
