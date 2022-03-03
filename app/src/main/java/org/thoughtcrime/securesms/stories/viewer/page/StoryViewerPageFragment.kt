package org.thoughtcrime.securesms.stories.viewer.page

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.doOnNextLayout
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import org.signal.core.util.DimensionUnit
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.AvatarImageView
import org.thoughtcrime.securesms.components.segmentedprogressbar.SegmentedProgressBar
import org.thoughtcrime.securesms.components.segmentedprogressbar.SegmentedProgressBarListener
import org.thoughtcrime.securesms.contacts.avatars.FallbackContactPhoto
import org.thoughtcrime.securesms.contacts.avatars.FallbackPhoto20dp
import org.thoughtcrime.securesms.contacts.avatars.GeneratedContactPhoto
import org.thoughtcrime.securesms.conversation.ConversationIntents
import org.thoughtcrime.securesms.conversation.colors.AvatarColor
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardBottomSheet
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragment
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragmentArgs
import org.thoughtcrime.securesms.database.AttachmentDatabase
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord
import org.thoughtcrime.securesms.mediapreview.MediaPreviewFragment
import org.thoughtcrime.securesms.mediapreview.VideoControlsDelegate
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.stories.StorySlateView
import org.thoughtcrime.securesms.stories.dialogs.StoryContextMenu
import org.thoughtcrime.securesms.stories.viewer.StoryViewerViewModel
import org.thoughtcrime.securesms.stories.viewer.reply.direct.StoryDirectReplyDialogFragment
import org.thoughtcrime.securesms.stories.viewer.reply.group.StoryGroupReplyBottomSheetDialogFragment
import org.thoughtcrime.securesms.stories.viewer.reply.tabs.StoryViewsAndRepliesDialogFragment
import org.thoughtcrime.securesms.stories.viewer.views.StoryViewsBottomSheetDialogFragment
import org.thoughtcrime.securesms.util.AvatarUtil
import org.thoughtcrime.securesms.util.BottomSheetUtil
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.LifecycleDisposable
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.fragments.requireListener
import org.thoughtcrime.securesms.util.views.TouchInterceptingFrameLayout
import org.thoughtcrime.securesms.util.visible
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class StoryViewerPageFragment : Fragment(R.layout.stories_viewer_fragment_page), MediaPreviewFragment.Events, MultiselectForwardBottomSheet.Callback, StorySlateView.Callback {

  private lateinit var progressBar: SegmentedProgressBar
  private lateinit var storySlate: StorySlateView
  private lateinit var viewsAndReplies: TextView

  private lateinit var callback: Callback

  private lateinit var chrome: List<View>
  private var animatorSet: AnimatorSet? = null

  private val viewModel: StoryViewerPageViewModel by viewModels(
    factoryProducer = {
      StoryViewerPageViewModel.Factory(storyRecipientId, StoryViewerPageRepository(requireContext()))
    }
  )

  private val sharedViewModel: StoryViewerViewModel by viewModels(
    ownerProducer = { requireParentFragment() }
  )

  private val videoControlsDelegate = VideoControlsDelegate()

  private val lifecycleDisposable = LifecycleDisposable()
  private val timeoutDisposable = LifecycleDisposable()

  private val storyRecipientId: RecipientId
    get() = requireArguments().getParcelable(ARG_STORY_RECIPIENT_ID)!!

  @SuppressLint("ClickableViewAccessibility")
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    callback = requireListener()

    val closeView: View = view.findViewById(R.id.close)
    val senderAvatar: AvatarImageView = view.findViewById(R.id.sender_avatar)
    val groupAvatar: AvatarImageView = view.findViewById(R.id.group_avatar)
    val from: TextView = view.findViewById(R.id.from)
    val date: TextView = view.findViewById(R.id.date)
    val moreButton: View = view.findViewById(R.id.more)
    val distributionList: TextView = view.findViewById(R.id.distribution_list)
    val cardWrapper: TouchInterceptingFrameLayout = view.findViewById(R.id.story_content_card_touch_interceptor)
    val card: CardView = view.findViewById(R.id.story_content_card)
    val caption: TextView = view.findViewById(R.id.story_caption)
    val largeCaption: TextView = view.findViewById(R.id.story_large_caption)
    val largeCaptionOverlay: View = view.findViewById(R.id.story_large_caption_overlay)

    storySlate = view.findViewById(R.id.story_slate)
    progressBar = view.findViewById(R.id.progress)
    viewsAndReplies = view.findViewById(R.id.views_and_replies_bar)

    storySlate.callback = this

    chrome = listOf(
      closeView,
      senderAvatar,
      groupAvatar,
      from,
      date,
      moreButton,
      distributionList,
      viewsAndReplies,
      progressBar
    )

    senderAvatar.setFallbackPhotoProvider(FallbackPhotoProvider())
    groupAvatar.setFallbackPhotoProvider(FallbackPhotoProvider())

    closeView.setOnClickListener {
      requireActivity().onBackPressed()
    }

    val gestureDetector = GestureDetectorCompat(
      requireContext(),
      StoryGestureListener(
        cardWrapper,
        viewModel::goToNextPost,
        viewModel::goToPreviousPost,
        this::startReply
      )
    )

    cardWrapper.setOnInterceptTouchEventListener { storySlate.state == StorySlateView.State.HIDDEN }
    cardWrapper.setOnTouchListener { _, event ->
      val result = gestureDetector.onTouchEvent(event)
      if (event.actionMasked == MotionEvent.ACTION_DOWN) {
        viewModel.setIsUserTouching(true)
        hideChrome()
      } else if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
        viewModel.setIsUserTouching(false)
        showChrome()
      }

      result
    }

    viewsAndReplies.setOnClickListener {
      startReply()
    }

    moreButton.setOnClickListener(this::displayMoreContextMenu)

    progressBar.listener = object : SegmentedProgressBarListener {
      override fun onPage(oldPageIndex: Int, newPageIndex: Int) {
        if (oldPageIndex != newPageIndex && context != null) {
          viewModel.setSelectedPostIndex(newPageIndex)
        }

        if (oldPageIndex == newPageIndex) {
          videoControlsDelegate.restart()
        }
      }

      override fun onFinished() {
        viewModel.goToNextPost()
      }

      override fun onRequestSegmentProgressPercentage(): Float? {
        val attachmentUri = if (viewModel.hasPost() && MediaUtil.isVideo(viewModel.getPost().attachment)) {
          viewModel.getPost().attachment.uri
        } else {
          null
        }

        return if (attachmentUri != null) {
          val playerState = videoControlsDelegate.getPlayerState(attachmentUri)
          if (playerState != null) {
            playerState.position.toFloat() / playerState.duration
          } else {
            null
          }
        } else {
          null
        }
      }
    }

    sharedViewModel.isScrolling.observe(viewLifecycleOwner) { isScrolling ->
      viewModel.setIsUserScrollingParent(isScrolling)
    }

    sharedViewModel.state.observe(viewLifecycleOwner) { parentState ->
      if (parentState.pages.size <= parentState.page) {
        viewModel.setIsSelectedPage(false)
      } else if (storyRecipientId == parentState.pages[parentState.page]) {
        if (progressBar.segmentCount != 0) {
          progressBar.reset()
          progressBar.setPosition(viewModel.getRestartIndex())
          videoControlsDelegate.restart()
        }
        viewModel.setIsSelectedPage(true)
      } else {
        viewModel.setIsSelectedPage(false)
      }
    }

    viewModel.state.observe(viewLifecycleOwner) { state ->
      if (state.posts.isNotEmpty() && state.selectedPostIndex < state.posts.size) {
        val post = state.posts[state.selectedPostIndex]

        presentViewsAndReplies(post)
        presentSenderAvatar(senderAvatar, post)
        presentGroupAvatar(groupAvatar, post)
        presentFrom(from, post)
        presentDate(date, post)
        presentDistributionList(distributionList, post)
        presentCaption(caption, largeCaption, largeCaptionOverlay, post)

        val durations: Map<Int, Long> = state.posts
          .mapIndexed { index, storyPost ->
            index to if (MediaUtil.isVideo(storyPost.attachment)) -1L else TimeUnit.SECONDS.toMillis(5)
          }
          .toMap()

        if (progressBar.segmentCount != state.posts.size || progressBar.segmentDurations != durations) {
          progressBar.segmentCount = state.posts.size
          progressBar.segmentDurations = durations
        }

        presentStory(post, state.selectedPostIndex)
        presentSlate(post)

        viewModel.setAreSegmentsInitialized(true)
      } else if (state.selectedPostIndex >= state.posts.size) {
        callback.onFinishedPosts(storyRecipientId)
      }
    }

    viewModel.storyViewerPlaybackState.observe(viewLifecycleOwner) { state ->
      if (state.isPaused) {
        pauseProgress()
      } else {
        resumeProgress()
      }
    }

    timeoutDisposable.bindTo(viewLifecycleOwner)
    lifecycleDisposable.bindTo(viewLifecycleOwner)
    lifecycleDisposable += viewModel.groupDirectReplyObservable.subscribe { opt ->
      if (opt.isPresent) {
        when (val sheet = opt.get()) {
          is StoryViewerDialog.GroupDirectReply -> {
            onStartDirectReply(sheet.storyId, sheet.recipientId)
          }
        }
      }
    }

    adjustConstraintsForScreenDimensions(viewsAndReplies, cardWrapper, card)
  }

  override fun onResume() {
    super.onResume()
    viewModel.setIsFragmentResumed(true)
  }

  override fun onPause() {
    super.onPause()
    viewModel.setIsFragmentResumed(false)
  }

  override fun onFinishForwardAction() = Unit

  override fun onDismissForwardSheet() {
    viewModel.setIsDisplayingForwardDialog(false)
  }

  private fun hideChrome() {
    animateChrome(0f)
  }

  private fun showChrome() {
    animateChrome(1f)
  }

  private fun animateChrome(alphaTarget: Float) {
    animatorSet?.cancel()
    animatorSet = AnimatorSet().apply {
      playTogether(
        chrome.map {
          ObjectAnimator.ofFloat(it, View.ALPHA, alphaTarget)
        }
      )
      start()
    }
  }

  private fun adjustConstraintsForScreenDimensions(
    viewsAndReplies: View,
    cardWrapper: View,
    card: CardView
  ) {
    val constraintSet = ConstraintSet()
    constraintSet.clone(requireView() as ConstraintLayout)

    when (StoryDisplay.getStoryDisplay(resources)) {
      StoryDisplay.LARGE -> {
        constraintSet.connect(viewsAndReplies.id, ConstraintSet.TOP, cardWrapper.id, ConstraintSet.BOTTOM)
        constraintSet.connect(viewsAndReplies.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        card.radius = DimensionUnit.DP.toPixels(18f)
      }
      StoryDisplay.MEDIUM -> {
        constraintSet.clear(viewsAndReplies.id, ConstraintSet.TOP)
        constraintSet.connect(viewsAndReplies.id, ConstraintSet.BOTTOM, cardWrapper.id, ConstraintSet.BOTTOM)
        card.radius = DimensionUnit.DP.toPixels(18f)
      }
      StoryDisplay.SMALL -> {
        constraintSet.clear(viewsAndReplies.id, ConstraintSet.TOP)
        constraintSet.connect(viewsAndReplies.id, ConstraintSet.BOTTOM, cardWrapper.id, ConstraintSet.BOTTOM)
        card.radius = DimensionUnit.DP.toPixels(0f)
      }
    }

    constraintSet.applyTo(requireView() as ConstraintLayout)
  }

  private fun resumeProgress() {
    if (progressBar.segmentCount != 0 && viewModel.hasPost()) {
      val postUri = viewModel.getPost().attachment.uri
      if (postUri != null) {
        progressBar.start()
        videoControlsDelegate.resume(postUri)
      }
    }
  }

  private fun pauseProgress() {
    progressBar.pause()
    videoControlsDelegate.pause()
  }

  private fun startReply() {
    val replyFragment: DialogFragment = when (viewModel.getSwipeToReplyState()) {
      StoryViewerPageState.ReplyState.NONE -> return
      StoryViewerPageState.ReplyState.SELF -> StoryViewsBottomSheetDialogFragment.create(viewModel.getPost().id)
      StoryViewerPageState.ReplyState.GROUP -> StoryGroupReplyBottomSheetDialogFragment.create(viewModel.getPost().id, viewModel.getPost().group!!.id)
      StoryViewerPageState.ReplyState.PRIVATE -> StoryDirectReplyDialogFragment.create(viewModel.getPost().id)
      StoryViewerPageState.ReplyState.GROUP_SELF -> StoryViewsAndRepliesDialogFragment.create(viewModel.getPost().id, viewModel.getPost().group!!.id, getViewsAndRepliesDialogStartPage())
    }

    if (viewModel.getSwipeToReplyState() == StoryViewerPageState.ReplyState.PRIVATE) {
      viewModel.setIsDisplayingDirectReplyDialog(true)
    } else {
      viewModel.setIsDisplayingViewsAndRepliesDialog(true)
    }

    replyFragment.showNow(childFragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
  }

  private fun onStartDirectReply(storyId: Long, recipientId: RecipientId) {
    viewModel.setIsDisplayingDirectReplyDialog(true)
    StoryDirectReplyDialogFragment.create(
      storyId = storyId,
      recipientId = recipientId
    ).show(childFragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
  }

  private fun getViewsAndRepliesDialogStartPage(): StoryViewsAndRepliesDialogFragment.StartPage {
    return if (viewModel.getPost().replyCount > 0) {
      StoryViewsAndRepliesDialogFragment.StartPage.REPLIES
    } else {
      StoryViewsAndRepliesDialogFragment.StartPage.VIEWS
    }
  }

  private fun presentStory(post: StoryPost, index: Int) {
    val fragment = childFragmentManager.findFragmentById(R.id.story_content_container)
    if (fragment != null && fragment.requireArguments().getParcelable<Uri>(MediaPreviewFragment.DATA_URI) == post.attachment.uri) {
      progressBar.setPosition(index)
      return
    }

    if (post.attachment.uri == null) {
      progressBar.setPosition(index)
      progressBar.invalidate()
    } else {
      progressBar.setPosition(index)
      storySlate.moveToState(StorySlateView.State.HIDDEN, post.id)
      viewModel.setIsDisplayingSlate(false)
      childFragmentManager.beginTransaction()
        .replace(R.id.story_content_container, createFragmentForPost(post))
        .commitNow()
    }
  }

  private fun presentSlate(post: StoryPost) {
    when (post.attachment.transferState) {
      AttachmentDatabase.TRANSFER_PROGRESS_DONE -> {
        storySlate.moveToState(StorySlateView.State.HIDDEN, post.id)
        viewModel.setIsDisplayingSlate(false)
      }
      AttachmentDatabase.TRANSFER_PROGRESS_PENDING -> {
        storySlate.moveToState(StorySlateView.State.LOADING, post.id)
        viewModel.setIsDisplayingSlate(true)
      }
      AttachmentDatabase.TRANSFER_PROGRESS_STARTED -> {
        storySlate.moveToState(StorySlateView.State.LOADING, post.id)
        viewModel.setIsDisplayingSlate(true)
      }
      AttachmentDatabase.TRANSFER_PROGRESS_FAILED -> {
        storySlate.moveToState(StorySlateView.State.NOT_FOUND, post.id)
        viewModel.setIsDisplayingSlate(true)
      }
    }
  }

  override fun onStateChanged(state: StorySlateView.State, postId: Long) {
    if (state == StorySlateView.State.LOADING || state == StorySlateView.State.RETRY) {
      timeoutDisposable.disposables.clear()
      timeoutDisposable += Observable.interval(10, TimeUnit.SECONDS)
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe {
          storySlate.moveToState(StorySlateView.State.ERROR, postId)
        }

      viewModel.forceDownloadSelectedPost()
    } else {
      timeoutDisposable.disposables.clear()
    }

    viewsAndReplies.visible = state == StorySlateView.State.HIDDEN
  }

  private fun presentDistributionList(distributionList: TextView, storyPost: StoryPost) {
    distributionList.text = storyPost.distributionList?.getDisplayName(requireContext())
    distributionList.visible = storyPost.distributionList != null && !storyPost.distributionList.isMyStory
  }

  @SuppressLint("SetTextI18n")
  private fun presentCaption(caption: TextView, largeCaption: TextView, largeCaptionOverlay: View, storyPost: StoryPost) {
    val displayBody = storyPost.conversationMessage.getDisplayBody(requireContext())
    caption.text = displayBody
    largeCaption.text = displayBody
    caption.visible = displayBody.isNotEmpty()
    caption.requestLayout()

    caption.doOnNextLayout {
      val maxLines = 5
      if (caption.lineCount > maxLines) {
        val lastCharShown = caption.layout.getLineVisibleEnd(maxLines - 1)
        caption.maxLines = maxLines

        val seeMore = (getString(R.string.StoryViewerPageFragment__see_more))

        val seeMoreWidth = caption.paint.measureText(seeMore)
        var offset = seeMore.length
        while (true) {
          val start = lastCharShown - offset
          if (start < 0) {
            break
          }

          val widthOfRemovedChunk = caption.paint.measureText(displayBody.subSequence(start, lastCharShown).toString())
          if (widthOfRemovedChunk > seeMoreWidth) {
            break
          }

          offset += 1
        }

        caption.text = displayBody.substring(0, lastCharShown - offset) + seeMore
        caption.setOnClickListener {
          onShowCaptionOverlay(caption, largeCaption, largeCaptionOverlay)
        }
      } else {
        caption.setOnClickListener(null)
      }
    }
  }

  private fun onShowCaptionOverlay(caption: TextView, largeCaption: TextView, largeCaptionOverlay: View) {
    caption.visible = false
    largeCaption.visible = true
    largeCaptionOverlay.visible = true
    largeCaptionOverlay.setOnClickListener {
      onHideCaptionOverlay(caption, largeCaption, largeCaptionOverlay)
    }
    viewModel.setIsDisplayingCaptionOverlay(true)
  }

  private fun onHideCaptionOverlay(caption: TextView, largeCaption: TextView, largeCaptionOverlay: View) {
    caption.visible = true
    largeCaption.visible = false
    largeCaptionOverlay.visible = false
    largeCaptionOverlay.setOnClickListener(null)
    viewModel.setIsDisplayingCaptionOverlay(false)
  }

  private fun presentFrom(from: TextView, storyPost: StoryPost) {
    val name = if (storyPost.sender.isSelf) {
      getString(R.string.StoryViewerPageFragment__you)
    } else {
      storyPost.sender.getDisplayName(requireContext())
    }

    if (storyPost.group != null) {
      from.text = getString(R.string.StoryViewerPageFragment__s_to_s, name, storyPost.group.getDisplayName(requireContext()))
    } else {
      from.text = name
    }
  }

  private fun presentDate(date: TextView, storyPost: StoryPost) {
    date.text = DateUtils.getBriefRelativeTimeSpanString(context, Locale.getDefault(), storyPost.dateInMilliseconds)
  }

  private fun presentSenderAvatar(senderAvatar: AvatarImageView, post: StoryPost) {
    AvatarUtil.loadIconIntoImageView(post.sender, senderAvatar, DimensionUnit.DP.toPixels(32f).toInt())
  }

  private fun presentGroupAvatar(groupAvatar: AvatarImageView, post: StoryPost) {
    if (post.group != null) {
      groupAvatar.setRecipient(post.group)
      groupAvatar.visible = true
    } else {
      groupAvatar.visible = false
    }
  }

  private fun presentViewsAndReplies(post: StoryPost) {
    val views = resources.getQuantityString(R.plurals.StoryViewerFragment__d_views, post.viewCount, post.viewCount)
    val replies = resources.getQuantityString(R.plurals.StoryViewerFragment__d_replies, post.replyCount, post.replyCount)

    if (Recipient.self() == post.sender) {
      if (post.replyCount == 0) {
        viewsAndReplies.text = views
      } else {
        viewsAndReplies.text = getString(R.string.StoryViewerFragment__s_s, views, replies)
      }
    } else if (post.replyCount > 0) {
      viewsAndReplies.text = replies
    } else {

      viewsAndReplies.setText(R.string.StoryViewerPageFragment__reply)
    }
  }

  private fun createFragmentForPost(storyPost: StoryPost): Fragment {
    return MediaPreviewFragment.newInstance(storyPost.attachment, false)
  }

  override fun getVideoControlsDelegate(): VideoControlsDelegate {
    return videoControlsDelegate
  }

  private fun displayMoreContextMenu(anchor: View) {
    viewModel.setIsDisplayingContextMenu(true)
    StoryContextMenu.show(
      context = requireContext(),
      anchorView = anchor,
      storyViewerPageState = viewModel.getStateSnapshot(),
      onDismiss = {
        viewModel.setIsDisplayingContextMenu(false)
      },
      onForward = { storyPost ->
        viewModel.setIsDisplayingForwardDialog(true)
        MultiselectForwardFragmentArgs.create(
          requireContext(),
          storyPost.conversationMessage.multiselectCollection.toSet(),
        ) {
          MultiselectForwardFragment.showBottomSheet(childFragmentManager, it)
        }
      },
      onGoToChat = {
        startActivity(ConversationIntents.createBuilder(requireContext(), storyRecipientId, -1L).build())
      },
      onHide = {
        lifecycleDisposable += viewModel.hideStory().subscribe {
          callback.onStoryHidden(storyRecipientId)
        }
      },
      onShare = {
        StoryContextMenu.share(this, it.conversationMessage.messageRecord as MediaMmsMessageRecord)
      },
      onSave = {
        StoryContextMenu.save(requireContext(), it.conversationMessage.messageRecord)
      },
      onDelete = {
        viewModel.setIsDisplayingDeleteDialog(true)
        lifecycleDisposable += StoryContextMenu.delete(requireContext(), setOf(it.conversationMessage.messageRecord)).subscribe { _ ->
          viewModel.setIsDisplayingDeleteDialog(false)
          viewModel.refresh()
        }
      }
    )
  }

  companion object {
    private const val ARG_STORY_RECIPIENT_ID = "arg.story.recipient.id"

    fun create(recipientId: RecipientId): Fragment {
      return StoryViewerPageFragment().apply {
        arguments = Bundle().apply {
          putParcelable(ARG_STORY_RECIPIENT_ID, recipientId)
        }
      }
    }
  }

  private class StoryGestureListener(
    private val container: View,
    private val onGoToNext: () -> Unit,
    private val onGoToPrevious: () -> Unit,
    private val onReplyToPost: () -> Unit
  ) : GestureDetector.SimpleOnGestureListener() {

    override fun onDown(e: MotionEvent?): Boolean {
      return true
    }

    override fun onFling(e1: MotionEvent?, e2: MotionEvent?, velocityX: Float, velocityY: Float): Boolean {
      val isSideSwipe = abs(velocityX) > abs(velocityY)
      if (!isSideSwipe) {
        return false
      }

      if (velocityX > 0) {
        onReplyToPost()
      }

      return true
    }

    override fun onSingleTapUp(e: MotionEvent): Boolean {
      if (e.x < container.measuredWidth * 0.25) {
        performLeftAction()
        return true
      } else if (e.x > container.measuredWidth - (container.measuredWidth * 0.25)) {
        performRightAction()
        return true
      }

      return false
    }

    private fun performLeftAction() {
      if (container.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
        onGoToNext()
      } else {
        onGoToPrevious()
      }
    }

    private fun performRightAction() {
      if (container.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
        onGoToPrevious()
      } else {
        onGoToNext()
      }
    }
  }

  private class FallbackPhotoProvider : Recipient.FallbackPhotoProvider() {
    override fun getPhotoForGroup(): FallbackContactPhoto {
      return FallbackPhoto20dp(R.drawable.ic_group_outline_20)
    }

    override fun getPhotoForResolvingRecipient(): FallbackContactPhoto {
      throw UnsupportedOperationException("This provider does not support resolving recipients")
    }

    override fun getPhotoForLocalNumber(): FallbackContactPhoto {
      throw UnsupportedOperationException("This provider does not support local number")
    }

    override fun getPhotoForRecipientWithName(name: String, targetSize: Int): FallbackContactPhoto {
      return FixedSizeGeneratedContactPhoto(name, R.drawable.ic_profile_outline_20)
    }

    override fun getPhotoForRecipientWithoutName(): FallbackContactPhoto {
      return FallbackPhoto20dp(R.drawable.ic_profile_outline_20)
    }
  }

  private class FixedSizeGeneratedContactPhoto(name: String, fallbackResId: Int) : GeneratedContactPhoto(name, fallbackResId) {
    override fun newFallbackDrawable(context: Context, color: AvatarColor, inverted: Boolean): Drawable {
      return FallbackPhoto20dp(fallbackResId).asDrawable(context, color, inverted)
    }
  }

  override fun singleTapOnMedia(): Boolean {
    return false
  }

  override fun mediaNotAvailable() {
  }

  interface Callback {
    fun onFinishedPosts(recipientId: RecipientId)
    fun onStoryHidden(recipientId: RecipientId)
  }
}
