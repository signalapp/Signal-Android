package org.thoughtcrime.securesms.stories.viewer.reply.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.signal.paging.ProxyPagingController
import org.thoughtcrime.securesms.database.model.MessageId

class StoryGroupReplyViewModel(storyId: Long, repository: StoryGroupReplyRepository) : ViewModel() {

  private val store = MutableStateFlow(StoryGroupReplyState())
  private val disposables = CompositeDisposable()

  val stateSnapshot: StoryGroupReplyState get() = store.value
  val state: Flow<StoryGroupReplyState> = store

  val pagingController: ProxyPagingController<MessageId> = ProxyPagingController()

  init {
    disposables += repository.getThreadId(storyId).subscribe { threadId ->
      store.update { it.copy(threadId = threadId) }
    }

    disposables += repository.getPagedReplies(storyId)
      .doOnNext { pagingController.set(it.controller) }
      .flatMap { it.data }
      .subscribeBy { data ->
        store.update { state ->
          state.copy(
            replies = data,
            loadState = StoryGroupReplyState.LoadState.READY
          )
        }
      }

    disposables += repository.getNameColorsMap(storyId)
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeBy { nameColors ->
        store.update { state ->
          state.copy(nameColors = nameColors)
        }
      }
  }

  override fun onCleared() {
    disposables.clear()
  }

  class Factory(private val storyId: Long, private val repository: StoryGroupReplyRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(StoryGroupReplyViewModel(storyId, repository)) as T
    }
  }
}
