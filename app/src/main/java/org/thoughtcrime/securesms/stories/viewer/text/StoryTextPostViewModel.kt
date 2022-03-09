package org.thoughtcrime.securesms.stories.viewer.text

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.thoughtcrime.securesms.database.model.databaseprotos.StoryTextPost
import org.thoughtcrime.securesms.util.Base64
import org.thoughtcrime.securesms.util.livedata.Store

class StoryTextPostViewModel(recordId: Long, repository: StoryTextPostRepository) : ViewModel() {

  private val store = Store(StoryTextPostState())
  private val disposables = CompositeDisposable()

  val state: LiveData<StoryTextPostState> = store.stateLiveData

  init {
    disposables += repository.getRecord(recordId)
      .map { record ->
        StoryTextPost.parseFrom(Base64.decode(record.body)) to record.linkPreviews.firstOrNull()
      }
      .subscribeBy(
        onSuccess = { (post, previews) ->
          store.update { state ->
            state.copy(
              storyTextPost = post,
              linkPreview = previews,
              loadState = StoryTextPostState.LoadState.LOADED
            )
          }
        },
        onError = {
          store.update { state ->
            state.copy(
              loadState = StoryTextPostState.LoadState.FAILED
            )
          }
        }
      )
  }

  override fun onCleared() {
    disposables.clear()
  }

  class Factory(private val recordId: Long, private val repository: StoryTextPostRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
      return modelClass.cast(StoryTextPostViewModel(recordId, repository)) as T
    }
  }
}
