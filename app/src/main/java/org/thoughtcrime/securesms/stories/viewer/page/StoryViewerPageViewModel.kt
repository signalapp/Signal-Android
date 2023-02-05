package org.thoughtcrime.securesms.stories.viewer.page

import androidx.annotation.CheckResult
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.livedata.Store
import org.thoughtcrime.securesms.util.rx.RxStore
import java.util.Optional
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

/**
 * Encapsulates presentation logic for displaying a collection of posts from a given user's story
 */
class StoryViewerPageViewModel(
  private val args: StoryViewerPageArgs,
  private val repository: StoryViewerPageRepository,
  val storyCache: StoryCache
) : ViewModel() {

  private val store = RxStore(StoryViewerPageState(isReceiptsEnabled = repository.isReadReceiptsEnabled()))
  private val disposables = CompositeDisposable()
  private val storyViewerDialogSubject: Subject<Optional<StoryViewerDialog>> = PublishSubject.create()
  private val storyLongPressSubject: Subject<Boolean> = PublishSubject.create()

  private val storyViewerPlaybackStore = Store(StoryViewerPlaybackState())

  val storyViewerPlaybackState: LiveData<StoryViewerPlaybackState> = storyViewerPlaybackStore.stateLiveData

  val groupDirectReplyObservable: Observable<Optional<StoryViewerDialog>> = storyViewerDialogSubject

  val state: Flowable<StoryViewerPageState> = store.stateFlowable
  val postContent: Flowable<Optional<StoryPost.Content>> = store.stateFlowable.map {
    Optional.ofNullable(it.posts.getOrNull(it.selectedPostIndex)?.content)
  }

  fun getStateSnapshot(): StoryViewerPageState = store.state

  init {
    refresh()
  }

  fun checkReadReceiptState() {
    val isReceiptsEnabledInState = getStateSnapshot().isReceiptsEnabled
    val isReceiptsEnabledInRepository = repository.isReadReceiptsEnabled()
    if (isReceiptsEnabledInState xor isReceiptsEnabledInRepository) {
      store.update {
        it.copy(isReceiptsEnabled = isReceiptsEnabledInRepository)
      }
    }
  }

  fun refresh() {
    disposables.clear()
    disposables += repository.getStoryPostsFor(args.recipientId, args.isOutgoingOnly).subscribe { posts ->
      store.update { state ->
        val isDisplayingInitialState = state.posts.isEmpty() && posts.isNotEmpty()
        val startIndex = if (state.posts.isEmpty() && args.initialStoryId > 0) {
          val initialIndex = posts.indexOfFirst { it.id == args.initialStoryId }
          initialIndex.takeIf { it > -1 } ?: state.selectedPostIndex
        } else if (state.posts.isEmpty()) {
          val initialPost = getNextUnreadPost(posts)
          val initialIndex = initialPost?.let { posts.indexOf(it) } ?: -1
          initialIndex.takeIf { it > -1 } ?: state.selectedPostIndex
        } else {
          state.selectedPostIndex
        }

        state.copy(
          isReady = true,
          posts = posts,
          replyState = resolveSwipeToReplyState(state, startIndex),
          selectedPostIndex = startIndex,
          isDisplayingInitialState = isDisplayingInitialState
        )
      }

      val attachments: List<Attachment> = posts.map { it.content }
        .filterIsInstance<StoryPost.Content.AttachmentContent>()
        .map { it.attachment }

      if (attachments.isNotEmpty()) {
        storyCache.prefetch(attachments)
      }
    }

    disposables += storyLongPressSubject.debounce(150, TimeUnit.MILLISECONDS).subscribe { isLongPress ->
      storyViewerPlaybackStore.update { it.copy(isUserLongTouching = isLongPress) }
    }
  }

  override fun onCleared() {
    disposables.clear()
    storyCache.clear()
    store.dispose()
  }

  fun hideStory(): Completable {
    return repository.hideStory(args.recipientId)
  }

  fun markViewed(storyPost: StoryPost) {
    repository.markViewed(storyPost)
  }

  fun setSelectedPostIndex(index: Int) {
    val selectedPost = getPostAt(index)

    if (selectedPost != null && selectedPost.content.transferState != AttachmentTable.TRANSFER_PROGRESS_DONE) {
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
    when {
      nextUnreadPost == null && args.isJumpForwardToUnviewed -> setSelectedPostIndex(store.state.posts.size)
      nextUnreadPost == null -> setSelectedPostIndex(postIndex + 1)
      else -> setSelectedPostIndex(store.state.posts.indexOf(nextUnreadPost))
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

  fun getPost(): StoryPost? {
    return if (hasPost()) {
      store.state.posts[store.state.selectedPostIndex]
    } else {
      null
    }
  }

  fun requirePost(): StoryPost {
    return getPost()!!
  }

  fun forceDownloadSelectedPost() {
    disposables += repository.forceDownload(requirePost()).subscribe()
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

  fun setIsUserScrollingChild(isUserScrollingChild: Boolean) {
    storyViewerPlaybackStore.update { it.copy(isUserScrollingChild = isUserScrollingChild) }
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

  fun setIsDisplayingHideDialog(isDisplayingHideDialog: Boolean) {
    storyViewerPlaybackStore.update { it.copy(isDisplayingHideDialog = isDisplayingHideDialog) }
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

  fun setIsDisplayingRecipientBottomSheet(isDisplayingRecipientBottomSheet: Boolean) {
    storyViewerPlaybackStore.update { it.copy(isDisplayingRecipientBottomSheet = isDisplayingRecipientBottomSheet) }
  }

  fun setIsUserTouching(isUserTouching: Boolean) {
    storyViewerPlaybackStore.update { it.copy(isUserTouching = isUserTouching) }
    storyLongPressSubject.onNext(isUserTouching)
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

  fun setIsDisplayingFirstTimeNavigation(isDisplayingFirstTimeNavigation: Boolean) {
    storyViewerPlaybackStore.update { it.copy(isDisplayingFirstTimeNavigation = isDisplayingFirstTimeNavigation) }
  }

  fun setIsDisplayingInfoDialog(isDisplayingInfoDialog: Boolean) {
    storyViewerPlaybackStore.update { it.copy(isDisplayingInfoDialog = isDisplayingInfoDialog) }
  }

  fun setIsUserScaling(isUserScaling: Boolean) {
    storyViewerPlaybackStore.update { it.copy(isUserScaling = isUserScaling) }
  }

  fun setIsDisplayingPartialSendDialog(isDisplayingPartialSendDialog: Boolean) {
    storyViewerPlaybackStore.update { it.copy(isDisplayingPartialSendDialog = isDisplayingPartialSendDialog) }
  }

  private fun resolveSwipeToReplyState(state: StoryViewerPageState, index: Int): StoryViewerPageState.ReplyState {
    if (index !in state.posts.indices) {
      return StoryViewerPageState.ReplyState.NONE
    }

    val post = state.posts[index]
    val message = post.conversationMessage.messageRecord
    val isFromSelf = post.sender.isSelf
    val isToGroup = post.group != null
    val isFailed = message.isFailed
    val isPartialSend = message.isIdentityMismatchFailure
    val isInProgress = !post.conversationMessage.messageRecord.isSent

    return when {
      isFromSelf && isPartialSend -> StoryViewerPageState.ReplyState.PARTIAL_SEND
      isFromSelf && isFailed -> StoryViewerPageState.ReplyState.SEND_FAILURE
      isFromSelf && isInProgress -> StoryViewerPageState.ReplyState.SENDING
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

  @CheckResult
  fun resend(storyPost: StoryPost): Completable {
    return repository
      .resend(storyPost.conversationMessage.messageRecord)
      .observeOn(AndroidSchedulers.mainThread())
  }

  class Factory(
    private val args: StoryViewerPageArgs,
    private val repository: StoryViewerPageRepository,
    private val storyCache: StoryCache
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(StoryViewerPageViewModel(args, repository, storyCache)) as T
    }
  }
}
