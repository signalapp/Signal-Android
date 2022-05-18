package org.thoughtcrime.securesms.stories.viewer.page

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject
import org.thoughtcrime.securesms.database.AttachmentDatabase
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.livedata.Store
import org.thoughtcrime.securesms.util.rx.RxStore
import java.util.Optional
import kotlin.math.max
import kotlin.math.min

/**
 * Encapsulates presentation logic for displaying a collection of posts from a given user's story
 */
class StoryViewerPageViewModel(
  private val recipientId: RecipientId,
  private val initialStoryId: Long,
  private val repository: StoryViewerPageRepository
) : ViewModel() {

  private val store = RxStore(StoryViewerPageState())
  private val disposables = CompositeDisposable()
  private val storyViewerDialogSubject: Subject<Optional<StoryViewerDialog>> = PublishSubject.create()

  private val storyViewerPlaybackStore = Store(StoryViewerPlaybackState())

  val storyViewerPlaybackState: LiveData<StoryViewerPlaybackState> = storyViewerPlaybackStore.stateLiveData

  val groupDirectReplyObservable: Observable<Optional<StoryViewerDialog>> = storyViewerDialogSubject

  val state: Flowable<StoryViewerPageState> = store.stateFlowable

  fun getStateSnapshot(): StoryViewerPageState = store.state

  init {
    refresh()
  }

  fun refresh() {
    disposables.clear()
    disposables += repository.getStoryPostsFor(recipientId).subscribe { posts ->
      store.update { state ->
        var isDisplayingInitialState = false
        val startIndex = if (state.posts.isEmpty() && initialStoryId > 0) {
          val initialIndex = posts.indexOfFirst { it.id == initialStoryId }
          isDisplayingInitialState = initialIndex > -1
          initialIndex.takeIf { it > -1 } ?: state.selectedPostIndex
        } else if (state.posts.isEmpty()) {
          val initialPost = getNextUnreadPost(posts)
          val initialIndex = initialPost?.let { posts.indexOf(it) } ?: -1
          initialIndex.takeIf { it > -1 } ?: state.selectedPostIndex
        } else {
          state.selectedPostIndex
        }

        state.copy(
          posts = posts,
          replyState = resolveSwipeToReplyState(state, startIndex),
          selectedPostIndex = startIndex,
          isDisplayingInitialState = isDisplayingInitialState
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

  fun markViewed(storyPost: StoryPost) {
    repository.markViewed(storyPost)
  }

  fun setSelectedPostIndex(index: Int) {
    val selectedPost = getPostAt(index)

    if (selectedPost != null && selectedPost.content.transferState != AttachmentDatabase.TRANSFER_PROGRESS_DONE) {
      disposables += repository.forceDownload(selectedPost).subscribe()
    }

    store.update {
      it.copy(
        selectedPostIndex = index,
        replyState = resolveSwipeToReplyState(it, index)
      )
    }
  }

  fun goToNextPost() {
    if (store.state.posts.isEmpty()) {
      return
    }

    val postIndex = store.state.selectedPostIndex
    val nextUnreadPost: StoryPost? = getNextUnreadPost(store.state.posts.drop(postIndex + 1))
    if (nextUnreadPost == null) {
      setSelectedPostIndex(postIndex + 1)
    } else {
      setSelectedPostIndex(store.state.posts.indexOf(nextUnreadPost))
    }
  }

  fun goToPreviousPost() {
    if (store.state.posts.isEmpty()) {
      return
    }

    val postIndex = store.state.selectedPostIndex
    val minIndex = if (store.state.isFirstPage) 0 else -1

    setSelectedPostIndex(max(minIndex, postIndex - 1))
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
    disposables += repository.forceDownload(getPost()).subscribe()
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

  fun setIsFirstPage(isFirstPage: Boolean) {
    store.update { it.copy(isFirstPage = isFirstPage) }
  }

  fun setIsSelectedPage(isSelectedPage: Boolean) {
    storyViewerPlaybackStore.update { it.copy(isSelectedPage = isSelectedPage) }
  }

  fun setIsDisplayingReactionAnimation(isDisplayingReactionAnimation: Boolean) {
    storyViewerPlaybackStore.update { it.copy(isDisplayingReactionAnimation = isDisplayingReactionAnimation) }
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

  fun setIsDisplayingLinkPreviewTooltip(isDisplayingLinkPreviewTooltip: Boolean) {
    storyViewerPlaybackStore.update { it.copy(isDisplayingLinkPreviewTooltip = isDisplayingLinkPreviewTooltip) }
  }

  fun setIsRunningSharedElementAnimation(isRunningSharedElementAnimation: Boolean) {
    storyViewerPlaybackStore.update { it.copy(isRunningSharedElementAnimation = isRunningSharedElementAnimation) }
  }

  private fun resolveSwipeToReplyState(state: StoryViewerPageState, index: Int): StoryViewerPageState.ReplyState {
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

  private fun getNextUnreadPost(list: List<StoryPost>): StoryPost? {
    return list.firstOrNull { !it.hasSelfViewed }
  }

  fun getPostAt(index: Int): StoryPost? {
    return store.state.posts.getOrNull(index)
  }

  class Factory(private val recipientId: RecipientId, private val initialStoryId: Long, private val repository: StoryViewerPageRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(StoryViewerPageViewModel(recipientId, initialStoryId, repository)) as T
    }
  }
}
