package org.thoughtcrime.securesms.stories.viewer.post

import android.graphics.Typeface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.model.databaseprotos.StoryTextPost
import org.thoughtcrime.securesms.stories.viewer.page.StoryPost
import org.thoughtcrime.securesms.util.Base64
import org.thoughtcrime.securesms.util.rx.RxStore
import kotlin.time.Duration.Companion.microseconds

class StoryPostViewModel(private val repository: StoryTextPostRepository) : ViewModel() {

  companion object {
    val TAG = Log.tag(StoryPostViewModel::class.java)
  }

  private val store: RxStore<StoryPostState> = RxStore(StoryPostState.None())
  private val disposables = CompositeDisposable()

  val state = store.stateFlowable.observeOn(AndroidSchedulers.mainThread())

  override fun onCleared() {
    store.dispose()
    disposables.clear()
  }

  fun onPostContentChanged(storyPostContent: StoryPost.Content) {
    disposables.clear()

    when (storyPostContent) {
      is StoryPost.Content.AttachmentContent -> {
        if (storyPostContent.uri == null) {
          store.update { StoryPostState.None() }
        } else if (storyPostContent.isVideo()) {
          store.update {
            StoryPostState.VideoPost(
              videoUri = storyPostContent.uri,
              size = storyPostContent.attachment.size,
              clipStart = storyPostContent.attachment.transformProperties.videoTrimStartTimeUs.microseconds,
              clipEnd = storyPostContent.attachment.transformProperties.videoTrimEndTimeUs.microseconds,
              blurHash = storyPostContent.attachment.blurHash
            )
          }
        } else {
          store.update { StoryPostState.ImagePost(storyPostContent.uri, storyPostContent.attachment.blurHash) }
        }
      }
      is StoryPost.Content.TextContent -> {
        loadTextContent(storyPostContent.recordId)
      }
    }
  }

  private fun loadTextContent(recordId: Long) {
    val typeface = repository.getTypeface(recordId)
      .doOnError { Log.w(TAG, "Failed to get typeface. Rendering with default.", it) }
      .onErrorReturn { Typeface.DEFAULT }

    val postAndPreviews = repository.getRecord(recordId)
      .map {
        if (it.body.isNotEmpty()) {
          StoryTextPost.parseFrom(Base64.decode(it.body)) to it.linkPreviews.firstOrNull()
        } else {
          throw Exception("Text post message body is empty.")
        }
      }

    disposables += Single.zip(typeface, postAndPreviews, ::Pair).subscribeBy(
      onSuccess = { (t, p) ->
        store.update {
          StoryPostState.TextPost(
            storyTextPost = p.first,
            linkPreview = p.second,
            typeface = t,
            loadState = StoryPostState.LoadState.LOADED
          )
        }
      },
      onError = {
        Log.d(TAG, "Couldn't load text post", it)
        store.update {
          StoryPostState.TextPost(
            loadState = StoryPostState.LoadState.FAILED
          )
        }
      }
    )
  }

  class Factory(private val repository: StoryTextPostRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(StoryPostViewModel(repository)) as T
    }
  }
}
