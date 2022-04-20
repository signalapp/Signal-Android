package org.thoughtcrime.securesms.stories.viewer

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.thoughtcrime.securesms.blurhash.BlurHash
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.stories.StoryTextPostModel
import org.thoughtcrime.securesms.util.rx.RxStore
import kotlin.math.max

class StoryViewerViewModel(
  private val startRecipientId: RecipientId,
  private val onlyIncludeHiddenStories: Boolean,
  storyThumbTextModel: StoryTextPostModel?,
  storyThumbUri: Uri?,
  storyThumbBlur: BlurHash?,
  private val recipientIds: List<RecipientId>,
  private val repository: StoryViewerRepository,
) : ViewModel() {

  private val store = RxStore(
    StoryViewerState(
      crossfadeSource = when {
        storyThumbTextModel != null -> StoryViewerState.CrossfadeSource.TextModel(storyThumbTextModel)
        storyThumbUri != null -> StoryViewerState.CrossfadeSource.ImageUri(storyThumbUri, storyThumbBlur)
        else -> StoryViewerState.CrossfadeSource.None
      }
    )
  )

  private val disposables = CompositeDisposable()

  val stateSnapshot: StoryViewerState get() = store.state
  val state: Flowable<StoryViewerState> = store.stateFlowable

  private val scrollStatePublisher: MutableLiveData<Boolean> = MutableLiveData(false)
  val isScrolling: LiveData<Boolean> = scrollStatePublisher

  private val childScrollStatePublisher: MutableLiveData<Boolean> = MutableLiveData(false)
  val isChildScrolling: LiveData<Boolean> = childScrollStatePublisher

  init {
    refresh()
  }

  fun setContentIsReady() {
    store.update {
      it.copy(loadState = it.loadState.copy(isContentReady = true))
    }
  }

  fun setCrossfaderIsReady() {
    store.update {
      it.copy(loadState = it.loadState.copy(isCrossfaderReady = true))
    }
  }

  fun setIsScrolling(isScrolling: Boolean) {
    scrollStatePublisher.value = isScrolling
  }

  private fun getStories(): Single<List<RecipientId>> {
    return if (recipientIds.isNotEmpty()) {
      Single.just(recipientIds)
    } else {
      repository.getStories(onlyIncludeHiddenStories)
    }
  }

  private fun refresh() {
    disposables.clear()
    disposables += getStories().subscribe { recipientIds ->
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

  fun onGoToNext(recipientId: RecipientId) {
    store.update {
      if (it.pages[it.page] == recipientId) {
        updatePages(it, it.page + 1)
      } else {
        it
      }
    }
  }

  fun onGoToPrevious(recipientId: RecipientId) {
    store.update {
      if (it.pages[it.page] == recipientId) {
        updatePages(it, max(0, it.page - 1))
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

  fun setIsChildScrolling(isChildScrolling: Boolean) {
    childScrollStatePublisher.value = isChildScrolling
  }

  class Factory(
    private val startRecipientId: RecipientId,
    private val onlyIncludeHiddenStories: Boolean,
    private val storyThumbTextModel: StoryTextPostModel?,
    private val storyThumbUri: Uri?,
    private val storyThumbBlur: BlurHash?,
    private val recipientIds: List<RecipientId>,
    private val repository: StoryViewerRepository
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
      return modelClass.cast(
        StoryViewerViewModel(
          startRecipientId,
          onlyIncludeHiddenStories,
          storyThumbTextModel,
          storyThumbUri,
          storyThumbBlur,
          recipientIds,
          repository
        )
      ) as T
    }
  }
}
