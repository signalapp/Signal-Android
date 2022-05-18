package org.thoughtcrime.securesms.stories.viewer.text

import android.graphics.Typeface
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.model.databaseprotos.StoryTextPost
import org.thoughtcrime.securesms.util.Base64
import org.thoughtcrime.securesms.util.livedata.Store

class StoryTextPostViewModel(recordId: Long, repository: StoryTextPostRepository) : ViewModel() {

  companion object {
    private val TAG = Log.tag(StoryTextPostViewModel::class.java)
  }

  private val store = Store(StoryTextPostState())
  private val disposables = CompositeDisposable()

  val state: LiveData<StoryTextPostState> = store.stateLiveData

  init {
    disposables += repository.getTypeface(recordId)
      .subscribeBy(
        onSuccess = { typeface ->
          store.update {
            it.copy(typeface = typeface)
          }
        },
        onError = { error ->
          Log.w(TAG, "Failed to get typeface. Rendering with default.", error)
          store.update {
            it.copy(typeface = Typeface.DEFAULT)
          }
        }
      )

    disposables += repository.getRecord(recordId)
      .map {
        if (it.body.isNotEmpty()) {
          StoryTextPost.parseFrom(Base64.decode(it.body)) to it.linkPreviews.firstOrNull()
        } else {
          throw Exception("Text post message body is empty.")
        }
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
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(StoryTextPostViewModel(recordId, repository)) as T
    }
  }
}
