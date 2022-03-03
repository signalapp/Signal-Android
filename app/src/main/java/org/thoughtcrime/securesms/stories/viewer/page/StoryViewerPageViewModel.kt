package org.thoughtcrime.securesms.stories.viewer.page

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.livedata.Store
import java.util.Optional
import kotlin.math.max
import kotlin.math.min

/**
 * Encapsulates presentation logic for displaying a collection of posts from a given user's story
 */
class StoryViewerPageViewModel(
  private val recipientId: RecipientId,
  private val repository: StoryViewerPageRepository
) : ViewModel() {

  private val store = Store(StoryViewerPageState())
  private val disposables = CompositeDisposable()
  private val storyViewerDialogSubject: Subject<Optional<StoryViewerDialog>> = PublishSubject.create()

  private val storyViewerPlaybackStore = Store(StoryViewerPlaybackState())

  val storyViewerPlaybackState: LiveData<StoryViewerPlaybackState> = storyViewerPlaybackStore.stateLiveData

  val groupDirectReplyObservable: Observable<Optional<StoryViewerDialog>> = storyViewerDialogSubject

  val state: LiveData<StoryViewerPageState> = store.stateLiveData

  fun getStateSnapshot(): StoryViewerPageState = store.state

  init {
    refresh()
  }

  fun refresh() {
    disposables.clear()
    disposables += repository.getStoryPostsFor(recipientId).subscribe { posts ->
      store.update {
        it.copy(
          posts = posts,
          replyState = resolveSwipeToReplyState(it)
        )
      }
    }
  }

  override fun onCleared() {
    disposables.clear()
  }

  fun hideStory(): Completable {
    return repository.hideStory(recipientId)
  }

  fun setSelectedPostIndex(index: Int) {
    val selectedPost = getPostAt(index)

    if (selectedPost != null) {
      repository.markViewed(selectedPost)
    }

    store.update {
      it.copy(
        selectedPostIndex = index,
        replyState = resolveSwipeToReplyState(it, index)
      )
    }
  }

  fun goToNextPost() {
    val postIndex = store.state.selectedPostIndex
    setSelectedPostIndex(postIndex + 1)
  }

  fun goToPreviousPost() {
    val postIndex = store.state.selectedPostIndex
    setSelectedPostIndex(max(0, postIndex - 1))
  }

  fun getRestartIndex(): Int {
    return min(store.state.selectedPostIndex, store.state.posts.lastIndex)
  }

  fun getSwipeToReplyState(): StoryViewerPageState.ReplyState {
    return store.state.replyState
  }

  fun hasPost(): Boolean {
    return store.state.selectedPostIndex in store.state.posts.indices
  }

  fun getPost(): StoryPost {
    return store.state.posts[store.state.selectedPostIndex]
  }

  fun forceDownloadSelectedPost() {
    repository.forceDownload(getPost())
  }

  fun startDirectReply(storyId: Long, recipientId: RecipientId) {
    storyViewerDialogSubject.onNext(Optional.of(StoryViewerDialog.GroupDirectReply(recipientId, storyId)))
  }

  fun setIsFragmentResumed(isFragmentResumed: Boolean) {
    storyViewerPlaybackStore.update { it.copy(isFragmentResumed = isFragmentResumed) }
  }

  fun setIsUserScrollingParent(isUserScrollingParent: Boolean) {
    storyViewerPlaybackStore.update { it.copy(isUserScrollingParent = isUserScrollingParent) }
  }

  fun setIsDisplayingSlate(isDisplayingSlate: Boolean) {
    storyViewerPlaybackStore.update { it.copy(isDisplayingSlate = isDisplayingSlate) }
  }

  fun setIsSelectedPage(isSelectedPage: Boolean) {
    storyViewerPlaybackStore.update { it.copy(isSelectedPage = isSelectedPage) }
  }

  fun setIsDisplayingContextMenu(isDisplayingContextMenu: Boolean) {
    storyViewerPlaybackStore.update { it.copy(isDisplayingContextMenu = isDisplayingContextMenu) }
  }

  fun setIsDisplayingForwardDialog(isDisplayingForwardDialog: Boolean) {
    storyViewerPlaybackStore.update { it.copy(isDisplayingForwardDialog = isDisplayingForwardDialog) }
  }

  fun setIsDisplayingDeleteDialog(isDisplayingDeleteDialog: Boolean) {
    storyViewerPlaybackStore.update { it.copy(isDisplayingDeleteDialog = isDisplayingDeleteDialog) }
  }

  fun setIsDisplayingViewsAndRepliesDialog(isDisplayingViewsAndRepliesDialog: Boolean) {
    storyViewerPlaybackStore.update { it.copy(isDisplayingViewsAndRepliesDialog = isDisplayingViewsAndRepliesDialog) }
  }

  fun setIsDisplayingDirectReplyDialog(isDisplayingDirectReplyDialog: Boolean) {
    storyViewerPlaybackStore.update { it.copy(isDisplayingDirectReplyDialog = isDisplayingDirectReplyDialog) }
  }

  fun setIsDisplayingCaptionOverlay(isDisplayingCaptionOverlay: Boolean) {
    storyViewerPlaybackStore.update { it.copy(isDisplayingCaptionOverlay = isDisplayingCaptionOverlay) }
  }

  fun setIsUserTouching(isUserTouching: Boolean) {
    storyViewerPlaybackStore.update { it.copy(isUserTouching = isUserTouching) }
  }

  fun setAreSegmentsInitialized(areSegmentsInitialized: Boolean) {
    storyViewerPlaybackStore.update { it.copy(areSegmentsInitialized = areSegmentsInitialized) }
  }

  private fun resolveSwipeToReplyState(state: StoryViewerPageState, index: Int = state.selectedPostIndex): StoryViewerPageState.ReplyState {
    if (index !in state.posts.indices) {
      return StoryViewerPageState.ReplyState.NONE
    }

    val post = state.posts[index]
    val isFromSelf = post.sender.isSelf
    val isToGroup = post.group != null

    return when {
      post.allowsReplies -> StoryViewerPageState.ReplyState.resolve(isFromSelf, isToGroup)
      isFromSelf -> StoryViewerPageState.ReplyState.SELF
      else -> StoryViewerPageState.ReplyState.NONE
    }
  }

  fun getPostAt(index: Int): StoryPost? {
    return store.state.posts.getOrNull(index)
  }

  class Factory(private val recipientId: RecipientId, private val repository: StoryViewerPageRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
      return modelClass.cast(StoryViewerPageViewModel(recipientId, repository)) as T
    }
  }
}
