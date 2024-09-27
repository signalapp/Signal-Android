package org.thoughtcrime.securesms.stories.viewer.page

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Rect
import android.media.AudioManager
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.method.ScrollingMovementMethod
import android.text.style.ClickableSpan
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.animation.Interpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.animation.PathInterpolatorCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.CircularProgressIndicatorSpec
import com.google.android.material.progressindicator.IndeterminateDrawable
import com.google.android.material.snackbar.Snackbar
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.signal.core.util.DimensionUnit
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.core.util.dp
import org.signal.core.util.getParcelableCompat
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.animation.AnimationCompleteListener
import org.thoughtcrime.securesms.components.AvatarImageView
import org.thoughtcrime.securesms.components.emoji.EmojiTextView
import org.thoughtcrime.securesms.components.segmentedprogressbar.SegmentedProgressBar
import org.thoughtcrime.securesms.components.segmentedprogressbar.SegmentedProgressBarListener
import org.thoughtcrime.securesms.components.spoiler.SpoilerAnnotation
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.conversation.ConversationIntents
import org.thoughtcrime.securesms.conversation.MessageStyler
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardBottomSheet
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragment
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragmentArgs
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList
import org.thoughtcrime.securesms.mediapreview.MediaPreviewFragment
import org.thoughtcrime.securesms.mediapreview.VideoControlsDelegate
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.ui.bottomsheet.RecipientBottomSheetDialogFragment
import org.thoughtcrime.securesms.safety.SafetyNumberBottomSheet
import org.thoughtcrime.securesms.stories.StorySlateView
import org.thoughtcrime.securesms.stories.StoryVolumeOverlayView
import org.thoughtcrime.securesms.stories.dialogs.StoryContextMenu
import org.thoughtcrime.securesms.stories.dialogs.StoryDialogs
import org.thoughtcrime.securesms.stories.viewer.AddToGroupStoryDelegate
import org.thoughtcrime.securesms.stories.viewer.StoryViewerViewModel
import org.thoughtcrime.securesms.stories.viewer.StoryVolumeViewModel
import org.thoughtcrime.securesms.stories.viewer.info.StoryInfoBottomSheetDialogFragment
import org.thoughtcrime.securesms.stories.viewer.post.StoryPostFragment
import org.thoughtcrime.securesms.stories.viewer.reply.direct.StoryDirectReplyDialogFragment
import org.thoughtcrime.securesms.stories.viewer.reply.group.StoryGroupReplyBottomSheetDialogFragment
import org.thoughtcrime.securesms.stories.viewer.reply.reaction.OnReactionSentView
import org.thoughtcrime.securesms.stories.viewer.reply.tabs.StoryViewsAndRepliesDialogFragment
import org.thoughtcrime.securesms.stories.viewer.views.StoryViewsBottomSheetDialogFragment
import org.thoughtcrime.securesms.util.AvatarUtil
import org.thoughtcrime.securesms.util.BottomSheetUtil
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.Debouncer
import org.thoughtcrime.securesms.util.Projection
import org.thoughtcrime.securesms.util.ServiceUtil
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.fragments.requireListener
import org.thoughtcrime.securesms.util.views.TouchInterceptingFrameLayout
import org.thoughtcrime.securesms.util.visible
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class StoryViewerPageFragment :
  Fragment(R.layout.stories_viewer_fragment_page),
  StoryPostFragment.Callback,
  MultiselectForwardBottomSheet.Callback,
  StorySlateView.Callback,
  StoryInfoBottomSheetDialogFragment.OnInfoSheetDismissedListener,
  SafetyNumberBottomSheet.Callbacks,
  RecipientBottomSheetDialogFragment.Callback {

  private val storyVolumeViewModel: StoryVolumeViewModel by viewModels(ownerProducer = { requireActivity() })

  private lateinit var progressBar: SegmentedProgressBar
  private lateinit var storySlate: StorySlateView
  private lateinit var viewsAndReplies: MaterialButton
  private lateinit var storyCaptionContainer: FrameLayout
  private lateinit var storyContentContainer: FrameLayout
  private lateinit var storyPageContainer: ConstraintLayout
  private lateinit var sendingBarTextView: TextView
  private lateinit var sendingBar: View
  private lateinit var storyNormalBottomGradient: View
  private lateinit var storyCaptionBottomGradient: View
  private lateinit var addToGroupStoryButton: MaterialButton

  private lateinit var callback: Callback

  private lateinit var chrome: List<View>
  private var animatorSet: AnimatorSet? = null

  private var volumeInAnimator: Animator? = null
  private var volumeOutAnimator: Animator? = null
  private var volumeDebouncer: Debouncer = Debouncer(3, TimeUnit.SECONDS)

  private val storyViewStateViewModel: StoryViewStateViewModel by viewModels()

  private var textStoryIntersectProcessingEvents: Boolean = false
  private val textStoryIntersectHitRect: Rect = Rect()

  private val viewModel: StoryViewerPageViewModel by viewModels(
    factoryProducer = {
      StoryViewerPageViewModel.Factory(
        storyViewerPageArgs,
        StoryViewerPageRepository(
          requireContext(),
          storyViewStateViewModel.storyViewStateCache
        ),
        StoryCache(
          Glide.with(requireActivity()),
          StoryDisplay.getStorySize(resources)
        )
      )
    }
  )

  private val sharedViewModel: StoryViewerViewModel by viewModels(
    ownerProducer = { requireParentFragment() }
  )

  private val videoControlsDelegate = VideoControlsDelegate()

  private val lifecycleDisposable = LifecycleDisposable()
  private val timeoutDisposable = LifecycleDisposable()

  private var sendingProgressDrawable: IndeterminateDrawable<CircularProgressIndicatorSpec>? = null

  private val storyViewerPageArgs: StoryViewerPageArgs by lazy(LazyThreadSafetyMode.NONE) {
    requireArguments().getParcelableCompat(ARGS, StoryViewerPageArgs::class.java)!!
  }

  @SuppressLint("ClickableViewAccessibility")
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    callback = requireListener()

    if (storyVolumeViewModel.snapshot.isMuted) {
      videoControlsDelegate.mute()
    } else {
      videoControlsDelegate.unmute()
    }

    val closeView: View = view.findViewById(R.id.close)
    val senderAvatar: AvatarImageView = view.findViewById(R.id.sender_avatar)
    val groupAvatar: AvatarImageView = view.findViewById(R.id.group_avatar)
    val from: TextView = view.findViewById(R.id.from)
    val date: TextView = view.findViewById(R.id.date)
    val moreButton: View = view.findViewById(R.id.more)
    val distributionList: TextView = view.findViewById(R.id.distribution_list)
    val cardWrapper: TouchInterceptingFrameLayout = view.findViewById(R.id.story_content_card_touch_interceptor)
    val card: MaterialCardView = view.findViewById(R.id.story_content_card)
    val caption: EmojiTextView = view.findViewById(R.id.story_caption)
    val largeCaption: EmojiTextView = view.findViewById(R.id.story_large_caption)
    val largeCaptionOverlay: View = view.findViewById(R.id.story_large_caption_overlay)
    val reactionAnimationView: OnReactionSentView = view.findViewById(R.id.on_reaction_sent_view)
    val storyGradientTop: View = view.findViewById(R.id.story_gradient_top)
    val storyGradientBottom: View = view.findViewById(R.id.story_bottom_gradient_container)
    val storyVolumeOverlayView: StoryVolumeOverlayView = view.findViewById(R.id.story_volume_overlay)
    val addToGroupStoryButtonWrapper: View = view.findViewById(R.id.add_wrapper)

    largeCaption.bindGestureListener()

    storyNormalBottomGradient = view.findViewById(R.id.story_gradient_bottom)
    storyCaptionBottomGradient = view.findViewById(R.id.story_caption_gradient)
    storyPageContainer = view.findViewById(R.id.story_page_container)
    storyContentContainer = view.findViewById(R.id.story_content_container)
    storyCaptionContainer = view.findViewById(R.id.story_caption_container)
    storySlate = view.findViewById(R.id.story_slate)
    progressBar = view.findViewById(R.id.progress)
    viewsAndReplies = view.findViewById(R.id.views_and_replies_bar)
    sendingBarTextView = view.findViewById(R.id.sending_text_view)
    sendingBar = view.findViewById(R.id.sending_bar)
    addToGroupStoryButton = view.findViewById(R.id.add)

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
      progressBar,
      storyGradientTop,
      storyGradientBottom,
      storyCaptionContainer,
      addToGroupStoryButtonWrapper
    )

    closeView.setOnClickListener {
      requireActivity().onBackPressed()
    }

    val addToGroupStoryDelegate = AddToGroupStoryDelegate(this)
    addToGroupStoryButton.setOnClickListener {
      addToGroupStoryDelegate.addToStory(storyViewerPageArgs.recipientId)
    }

    val singleTapHandler = SingleTapHandler(
      cardWrapper,
      viewModel::goToNextPost,
      viewModel::goToPreviousPost
    )

    val gestureDetector = GestureDetectorCompat(
      requireContext(),
      StoryGestureListener(
        cardWrapper,
        singleTapHandler,
        this::startReply,
        requireListener<Callback>()::onContentTranslation,
        sharedViewModel = sharedViewModel
      )
    )

    gestureDetector.setOnDoubleTapListener(null)

    val scaleListener = StoryScaleListener(
      viewModel,
      sharedViewModel,
      card
    )

    val scaleDetector = ScaleGestureDetector(
      requireContext(),
      scaleListener
    )

    cardWrapper.setOnInterceptTouchEventListener {
      !storySlate.state.hasClickableContent && !checkEventIntersectsClickableSpan(cardWrapper, it)
    }

    cardWrapper.setOnTouchListener { _, event ->
      scaleDetector.onTouchEvent(event)
      val result = if (scaleDetector.isInProgress || scaleListener.isPerformingEndAnimation) {
        true
      } else {
        gestureDetector.onTouchEvent(event)
      }

      if (event.actionMasked == MotionEvent.ACTION_DOWN) {
        viewModel.setIsUserTouching(true)
      } else if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
        viewModel.setIsUserTouching(false)

        val canCloseFromHorizontalSlide = requireView().translationX > DimensionUnit.DP.toPixels(56f)
        val canCloseFromVerticalSlide = requireView().translationY > DimensionUnit.DP.toPixels(56f) || requireView().translationY < -DimensionUnit.DP.toPixels(56f)
        if ((canCloseFromHorizontalSlide || canCloseFromVerticalSlide) && event.actionMasked == MotionEvent.ACTION_UP) {
          requireActivity().onBackPressed()
        } else {
          sharedViewModel.setIsChildScrolling(false)
          requireView().animate()
            .setInterpolator(StoryGestureListener.INTERPOLATOR)
            .setDuration(100)
            .translationX(0f)
            .translationY(0f)

          requireListener<Callback>().onContentTranslation(0f, 0f)
        }
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
          Log.d(TAG, "onPage: Moving from $oldPageIndex to $newPageIndex")
          viewModel.setSelectedPostIndex(newPageIndex)
        }
      }

      override fun onFinished() {
        viewModel.goToNextPost()
      }

      override fun onRequestSegmentProgressPercentage(): Float? {
        val storyPost = viewModel.getPost() ?: return null
        val attachmentUri = if (storyPost.content.isVideo()) {
          storyPost.content.uri
        } else {
          null
        }

        return if (attachmentUri != null) {
          val playerState = videoControlsDelegate.getPlayerState(attachmentUri)
          if (playerState != null) {
            getVideoPlaybackPosition(playerState) / getVideoPlaybackDuration(playerState)
          } else {
            null
          }
        } else {
          null
        }
      }
    }

    reactionAnimationView.callback = object : OnReactionSentView.Callback {
      override fun onFinished() {
        viewModel.setIsDisplayingReactionAnimation(false)
      }
    }

    sharedViewModel.isScrolling.observe(viewLifecycleOwner) { isScrolling ->
      viewModel.setIsUserScrollingParent(isScrolling)
    }

    lifecycleDisposable += sharedViewModel.isChildScrolling.subscribe {
      viewModel.setIsUserScrollingChild(it)
    }

    lifecycleDisposable += sharedViewModel.isFirstTimeNavigationShowing.subscribe {
      viewModel.setIsDisplayingFirstTimeNavigation(it)
    }

    lifecycleDisposable += storyVolumeViewModel.state.distinctUntilChanged().observeOn(AndroidSchedulers.mainThread()).subscribe { volumeState ->
      if (volumeState.isMuted) {
        videoControlsDelegate.mute()
        return@subscribe
      }

      if (!viewModel.hasPost() || viewModel.getPost()?.content?.isVideo() != true || volumeState.level < 0) {
        return@subscribe
      }

      if (!volumeState.isMuted) {
        videoControlsDelegate.unmute()
      }

      val audioManager = ServiceUtil.getAudioManager(requireContext())
      if (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) != volumeState.level) {
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volumeState.level, 0)
        storyVolumeOverlayView.setVolumeLevel(volumeState.level)
        storyVolumeOverlayView.setVideoHaNoAudio(!videoControlsDelegate.hasAudioStream())
        displayStoryVolumeOverlayForTimeout(storyVolumeOverlayView)
      }
    }

    lifecycleDisposable += sharedViewModel.loadState.subscribe {
      viewModel.setIsRunningSharedElementAnimation(!it.isCrossfaderReady)
      storyContentContainer.visible = it.isCrossfaderReady
    }

    lifecycleDisposable += sharedViewModel.state.distinctUntilChanged().observeOn(AndroidSchedulers.mainThread()).subscribe { parentState ->
      if (parentState.pages.size <= parentState.page) {
        viewModel.setIsSelectedPage(false)
      } else if (storyViewerPageArgs.recipientId == parentState.pages[parentState.page]) {
        if (progressBar.segmentCount != 0) {
          progressBar.reset()
          progressBar.setPosition(viewModel.getRestartIndex())
          videoControlsDelegate.restart()
        }
        viewModel.setIsFirstPage(parentState.page == 0)
        viewModel.setIsSelectedPage(true)
      } else {
        viewModel.setIsSelectedPage(false)
      }
    }

    lifecycleDisposable += viewModel.state.observeOn(AndroidSchedulers.mainThread()).subscribe { state ->
      if (!state.isReady) {
        return@subscribe
      }

      if (context == null) {
        Log.d(TAG, "Subscriber called while fragment is detached. Ignoring state update.")
        return@subscribe
      }

      if (state.posts.isNotEmpty() && state.selectedPostIndex in state.posts.indices) {
        val post = state.posts[state.selectedPostIndex]

        addToGroupStoryButton.visible = post.group != null

        presentBottomBar(post, state.replyState, state.isReceiptsEnabled)
        presentSenderAvatar(senderAvatar, post)
        presentGroupAvatar(groupAvatar, post)
        presentFrom(from, post)
        presentDate(date, post)
        presentDistributionList(distributionList, post)
        presentCaption(caption, largeCaption, largeCaptionOverlay, post)

        val durations: Map<Int, Long> = state.posts
          .mapIndexed { index, storyPost ->
            index to when {
              storyPost.sender.isReleaseNotes -> ONBOARDING_DURATION
              storyPost.content.isVideo() -> -1L
              storyPost.content is StoryPost.Content.TextContent -> calculateDurationForText(storyPost.content)
              storyPost.content is StoryPost.Content.AttachmentContent -> calculateDurationForAttachment(storyPost.content)
              else -> DEFAULT_DURATION
            }
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
        callback.onFinishedPosts(storyViewerPageArgs.recipientId)
      } else if (state.selectedPostIndex < 0) {
        callback.onGoToPreviousStory(storyViewerPageArgs.recipientId)
      }

      if (state.isDisplayingInitialState && !sharedViewModel.hasConsumedInitialState) {
        sharedViewModel.consumeInitialState()
        if (storyViewerPageArgs.source == StoryViewerPageArgs.Source.NOTIFICATION) {
          startReply(isFromNotification = true, groupReplyStartPosition = storyViewerPageArgs.groupReplyStartPosition)
        } else if (storyViewerPageArgs.source == StoryViewerPageArgs.Source.INFO_CONTEXT && state.selectedPostIndex in state.posts.indices) {
          viewModel.setIsDisplayingInfoDialog(true)
          lifecycleDisposable += sharedViewModel.postAfterLoadStateReady {
            showInfo(state.posts[state.selectedPostIndex])
          }
        }
      }
    }

    viewModel.storyViewerPlaybackState.observe(viewLifecycleOwner) { state ->
      if (state.isPaused) {
        pauseProgress()
      } else {
        resumeProgress()
      }

      when {
        state.hideChromeImmediate -> {
          hideChromeImmediate()
        }
        state.hideChrome -> {
          hideChrome()
        }
        else -> {
          showChrome()
        }
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
          StoryViewerDialog.Delete,
          StoryViewerDialog.Forward -> Unit
        }
      }
    }

    adjustConstraintsForScreenDimensions(viewsAndReplies, cardWrapper, card)

    childFragmentManager.setFragmentResultListener(StoryDirectReplyDialogFragment.REQUEST_EMOJI, viewLifecycleOwner) { _, bundle ->
      val emoji = bundle.getString(StoryDirectReplyDialogFragment.REQUEST_EMOJI)
      if (emoji != null) {
        reactionAnimationView.playForEmoji(listOf(emoji))
        viewModel.setIsDisplayingReactionAnimation(true)
      }
    }
  }

  override fun onResume() {
    super.onResume()
    viewModel.setIsFragmentResumed(true)
    viewModel.checkReadReceiptState()
    markViewedIfAble()
  }

  override fun onPause() {
    super.onPause()
    viewModel.setIsFragmentResumed(false)
  }

  override fun onDestroyView() {
    super.onDestroyView()
    childFragmentManager.fragments.forEach {
      if (it is MediaPreviewFragment) {
        it.cleanUp()
      }
    }

    volumeDebouncer.clear()
  }

  override fun onFinishForwardAction() = Unit

  override fun onDismissForwardSheet() {
    viewModel.setIsDisplayingForwardDialog(false)
  }

  private fun checkEventIntersectsClickableSpan(cardWrapper: ViewGroup, event: MotionEvent): Boolean {
    if (viewModel.getPost()?.content?.isText() != true) {
      textStoryIntersectProcessingEvents = false
      return false
    }

    val action = event.action
    if (action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_UP) {
      return textStoryIntersectProcessingEvents
    }

    if (checkTextSpanIntersect(cardWrapper, event)) {
      textStoryIntersectProcessingEvents = true
      return true
    }

    if (checkLinkPreviewIntersect(cardWrapper, event)) {
      textStoryIntersectProcessingEvents = true
      return true
    }

    return false
  }

  private fun checkTextSpanIntersect(cardWrapper: ViewGroup, event: MotionEvent): Boolean {
    val textView = cardWrapper.findViewById<TextView>(R.id.text_story_post_text)
    val spanned = textView.text as? Spanned ?: return false

    val textViewProjection = Projection.relativeToParent(cardWrapper, textView, null)
    var x = event.x - textViewProjection.x
    var y = event.y - textViewProjection.y

    textViewProjection.release()

    x -= textView.totalPaddingLeft
    y -= textView.totalPaddingTop

    x += textView.scrollX
    y += textView.scrollY

    val layout = textView.layout
    val line = layout.getLineForVertical(y.toInt())
    val off = layout.getOffsetForHorizontal(line, x)

    val spoilers = spanned.getSpans(off, off, SpoilerAnnotation.SpoilerClickableSpan::class.java)
    if (spoilers.isNotEmpty()) {
      return true
    }

    val clickables = spanned.getSpans(off, off, ClickableSpan::class.java)
    return clickables.isNotEmpty()
  }

  private fun checkLinkPreviewIntersect(cardWrapper: ViewGroup, event: MotionEvent): Boolean {
    Log.d(TAG, "Checking motion event for link preview intersect: ${event.x} ${event.y}")

    val linkPreviewView = cardWrapper.findViewById<View>(R.id.text_story_post_link_preview)
    val viewProjection = Projection.relativeToParent(cardWrapper, linkPreviewView, null)
      .translateY(linkPreviewView.translationY)

    textStoryIntersectHitRect.set(
      viewProjection.x.toInt(),
      viewProjection.y.toInt(),
      viewProjection.x.toInt() + viewProjection.width,
      viewProjection.y.toInt() + viewProjection.height
    )

    viewProjection.release()

    Log.d(TAG, "${event.x}, ${event.y} within $textStoryIntersectHitRect? ${textStoryIntersectHitRect.contains(event.x.toInt(), event.y.toInt())}")

    return textStoryIntersectHitRect.contains(event.x.toInt(), event.y.toInt())
  }

  private fun calculateDurationForText(textContent: StoryPost.Content.TextContent): Long {
    return calculateDurationForContentLength(textContent.length)
  }

  private fun calculateDurationForAttachment(attachmentContent: StoryPost.Content.AttachmentContent): Long {
    val caption: String? = attachmentContent.attachment.caption
    return if (caption.isNullOrEmpty()) {
      DEFAULT_DURATION
    } else {
      max(DEFAULT_DURATION, calculateDurationForContentLength(caption.length))
    }
  }

  private fun calculateDurationForContentLength(contentLength: Int): Long {
    val divisionsOf15 = contentLength / CHARACTERS_PER_SECOND
    return TimeUnit.SECONDS.toMillis(divisionsOf15) + MIN_TEXT_STORY_PLAYBACK
  }

  private fun getVideoPlaybackPosition(playerState: VideoControlsDelegate.PlayerState): Float {
    return if (playerState.isGif) {
      playerState.position.toFloat() + (playerState.duration * playerState.loopCount)
    } else {
      playerState.position.toFloat()
    }
  }

  private fun getVideoPlaybackDuration(playerState: VideoControlsDelegate.PlayerState): Long {
    return if (playerState.isGif) {
      val timeToPlayMinLoops = playerState.duration * MIN_GIF_LOOPS
      max(MIN_GIF_PLAYBACK_DURATION, timeToPlayMinLoops)
    } else {
      min(playerState.duration, MAX_VIDEO_PLAYBACK_DURATION)
    }
  }

  private fun displayStoryVolumeOverlayForTimeout(view: View) {
    if (volumeInAnimator?.isRunning != true) {
      volumeOutAnimator?.cancel()
      volumeInAnimator = ObjectAnimator.ofFloat(view, View.ALPHA, 1f).apply {
        duration = 200
        start()
      }
    }

    volumeDebouncer.publish {
      if (volumeOutAnimator?.isRunning != true) {
        volumeInAnimator?.cancel()
        volumeOutAnimator = ObjectAnimator.ofFloat(view, View.ALPHA, 0f).apply {
          duration = 200
          start()
        }
      }
    }
  }

  private fun hideChromeImmediate() {
    animatorSet?.cancel()
    chrome.map {
      it.alpha = 0f
    }
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
      duration = 100
      interpolator = StoryGestureListener.INTERPOLATOR
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
    card: MaterialCardView
  ) {
    val constraintSet = ConstraintSet()
    constraintSet.clone(storyPageContainer)

    when (StoryDisplay.getStoryDisplay(resources.displayMetrics.widthPixels.toFloat(), resources.displayMetrics.heightPixels.toFloat())) {
      StoryDisplay.LARGE -> {
        constraintSet.setDimensionRatio(cardWrapper.id, "9:16")
        constraintSet.connect(viewsAndReplies.id, ConstraintSet.TOP, cardWrapper.id, ConstraintSet.BOTTOM)
        constraintSet.connect(viewsAndReplies.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        card.radius = DimensionUnit.DP.toPixels(18f)
      }
      StoryDisplay.MEDIUM -> {
        constraintSet.setDimensionRatio(cardWrapper.id, "9:16")
        constraintSet.clear(viewsAndReplies.id, ConstraintSet.TOP)
        constraintSet.connect(viewsAndReplies.id, ConstraintSet.BOTTOM, cardWrapper.id, ConstraintSet.BOTTOM)
        card.radius = DimensionUnit.DP.toPixels(18f)
      }
      StoryDisplay.SMALL -> {
        constraintSet.setDimensionRatio(cardWrapper.id, null)
        constraintSet.clear(viewsAndReplies.id, ConstraintSet.TOP)
        constraintSet.connect(viewsAndReplies.id, ConstraintSet.BOTTOM, cardWrapper.id, ConstraintSet.BOTTOM)
        card.radius = DimensionUnit.DP.toPixels(0f)
      }
    }

    constraintSet.applyTo(storyPageContainer)
  }

  private fun resumeProgress() {
    val storyPost = viewModel.getPost() ?: return
    if (progressBar.segmentCount != 0) {
      val postUri = storyPost.content.uri
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

  private fun startReply(isFromNotification: Boolean = false, groupReplyStartPosition: Int = -1) {
    val storyPost = viewModel.getPost() ?: return
    val storyPostId: Long = storyPost.id
    val replyFragment: DialogFragment = when (viewModel.getSwipeToReplyState()) {
      StoryViewerPageState.ReplyState.NONE -> return
      StoryViewerPageState.ReplyState.SELF -> StoryViewsBottomSheetDialogFragment.create(storyPostId)
      StoryViewerPageState.ReplyState.GROUP -> StoryGroupReplyBottomSheetDialogFragment.create(
        storyPostId,
        storyPost.group!!.id,
        isFromNotification,
        groupReplyStartPosition
      )
      StoryViewerPageState.ReplyState.PRIVATE -> StoryDirectReplyDialogFragment.create(storyPostId)
      StoryViewerPageState.ReplyState.GROUP_SELF -> StoryViewsAndRepliesDialogFragment.create(
        storyPostId,
        storyPost.group!!.id,
        if (isFromNotification) StoryViewsAndRepliesDialogFragment.StartPage.REPLIES else getViewsAndRepliesDialogStartPage(),
        isFromNotification,
        groupReplyStartPosition
      )
      StoryViewerPageState.ReplyState.PARTIAL_SEND -> {
        handleResend(storyPost)
        return
      }
      StoryViewerPageState.ReplyState.SEND_FAILURE -> {
        handleResend(storyPost)
        return
      }
      StoryViewerPageState.ReplyState.SENDING -> return
    }

    if (viewModel.getSwipeToReplyState() == StoryViewerPageState.ReplyState.PRIVATE) {
      viewModel.setIsDisplayingDirectReplyDialog(true)
    } else {
      viewModel.setIsDisplayingViewsAndRepliesDialog(true)
    }

    replyFragment.showNow(childFragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
  }

  private fun handleResend(storyPost: StoryPost) {
    viewModel.setIsDisplayingPartialSendDialog(true)
    if (storyPost.conversationMessage.messageRecord.isIdentityMismatchFailure) {
      SafetyNumberBottomSheet
        .forMessageRecord(requireContext(), storyPost.conversationMessage.messageRecord)
        .show(childFragmentManager)
    } else {
      StoryDialogs.resendStory(requireContext(), { viewModel.setIsDisplayingPartialSendDialog(false) }) {
        lifecycleDisposable += viewModel.resend(storyPost).subscribe()
      }
    }
  }

  private fun showInfo(storyPost: StoryPost) {
    viewModel.setIsDisplayingInfoDialog(true)
    StoryInfoBottomSheetDialogFragment.create(storyPost.id).show(childFragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
  }

  private fun markViewedIfAble() {
    val post = viewModel.getPost() ?: return
    if (post.content.transferState == AttachmentTable.TRANSFER_PROGRESS_DONE) {
      if (isResumed) {
        viewModel.markViewed(post)
      }
    }
  }

  private fun onStartDirectReply(storyId: Long, recipientId: RecipientId) {
    viewModel.setIsDisplayingDirectReplyDialog(true)
    StoryDirectReplyDialogFragment.create(
      storyId = storyId,
      recipientId = recipientId
    ).show(childFragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
  }

  private fun getViewsAndRepliesDialogStartPage(): StoryViewsAndRepliesDialogFragment.StartPage {
    return if (viewModel.requirePost().replyCount > 0) {
      StoryViewsAndRepliesDialogFragment.StartPage.REPLIES
    } else {
      StoryViewsAndRepliesDialogFragment.StartPage.VIEWS
    }
  }

  private fun presentStory(post: StoryPost, index: Int) {
    if (post.content.uri == null) {
      progressBar.setPosition(index)
      progressBar.invalidate()
    } else {
      progressBar.setPosition(index)
      storySlate.moveToState(StorySlateView.State.HIDDEN, post.id)
      viewModel.setIsDisplayingSlate(false)
    }
  }

  private fun presentSlate(post: StoryPost) {
    storySlate.setBackground((post.conversationMessage.messageRecord as? MmsMessageRecord)?.slideDeck?.thumbnailSlide?.placeholderBlur)

    if (post.conversationMessage.messageRecord.isOutgoing) {
      storySlate.moveToState(StorySlateView.State.HIDDEN, post.id)
      viewModel.setIsDisplayingSlate(false)
      return
    }

    when (post.content.transferState) {
      AttachmentTable.TRANSFER_PROGRESS_DONE -> {
        storySlate.moveToState(StorySlateView.State.HIDDEN, post.id)
        viewModel.setIsDisplayingSlate(false)
        markViewedIfAble()
      }
      AttachmentTable.TRANSFER_PROGRESS_PENDING -> {
        Log.d(TAG, "Story content download is pending.")
        storySlate.moveToState(StorySlateView.State.LOADING, post.id)
        sharedViewModel.setContentIsReady()
        viewModel.setIsDisplayingSlate(true)
      }
      AttachmentTable.TRANSFER_PROGRESS_STARTED -> {
        Log.d(TAG, "Story content download is in progress.")
        storySlate.moveToState(StorySlateView.State.LOADING, post.id)
        sharedViewModel.setContentIsReady()
        viewModel.setIsDisplayingSlate(true)
      }
      AttachmentTable.TRANSFER_PROGRESS_FAILED -> {
        Log.d(TAG, "Story content download has failed temporarily.")
        storySlate.moveToState(StorySlateView.State.ERROR, post.id)
        sharedViewModel.setContentIsReady()
        viewModel.setIsDisplayingSlate(true)
      }
      AttachmentTable.TRANSFER_PROGRESS_PERMANENT_FAILURE -> {
        Log.d(TAG, "Story content download has failed permanently.")
        storySlate.moveToState(StorySlateView.State.FAILED, post.id, post.sender)
        sharedViewModel.setContentIsReady()
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
  private fun presentCaption(caption: EmojiTextView, largeCaption: EmojiTextView, largeCaptionOverlay: View, storyPost: StoryPost) {
    val displayBody: CharSequence = if (storyPost.content is StoryPost.Content.AttachmentContent) {
      val displayBodySpan = SpannableString(storyPost.content.attachment.caption ?: "")
      val ranges: BodyRangeList? = storyPost.conversationMessage.messageRecord.messageRanges
      if (ranges != null && displayBodySpan.isNotEmpty()) {
        MessageStyler.style(storyPost.conversationMessage.messageRecord.dateSent, ranges, displayBodySpan)
      }

      displayBodySpan
    } else {
      ""
    }

    storyNormalBottomGradient.visible = displayBody.isEmpty()
    storyCaptionBottomGradient.visible = displayBody.isNotEmpty()

    caption.text = displayBody
    largeCaption.text = displayBody
    caption.visible = displayBody.isNotEmpty()
    caption.requestLayout()
    caption.movementMethod = LinkMovementMethod.getInstance()
    caption.setOverflowText(getString(R.string.StoryViewerPageFragment__see_more))
    caption.maxLines = 5
    caption.text = displayBody
    caption.setMaxLength(280)

    if (caption.text.length == displayBody.length) {
      caption.setOnClickListener(null)
      caption.isClickable = false
    } else {
      caption.setOnClickListener {
        onShowCaptionOverlay(caption, largeCaption, largeCaptionOverlay)
      }
    }
  }

  private fun onShowCaptionOverlay(caption: TextView, largeCaption: TextView, largeCaptionOverlay: View) {
    sharedViewModel.setIsChildScrolling(true)

    caption.visible = false
    largeCaption.visible = true
    largeCaptionOverlay.visible = true
    largeCaption.movementMethod = ScrollingMovementMethod()
    largeCaption.scrollY = 0
    largeCaption.setOnClickListener {
      onHideCaptionOverlay(caption, largeCaption, largeCaptionOverlay)
    }
    largeCaptionOverlay.setOnClickListener {
      onHideCaptionOverlay(caption, largeCaption, largeCaptionOverlay)
    }
    viewModel.setIsDisplayingCaptionOverlay(true)
  }

  private fun onHideCaptionOverlay(caption: TextView, largeCaption: TextView, largeCaptionOverlay: View) {
    caption.visible = true
    largeCaption.visible = false
    largeCaptionOverlay.visible = false
    largeCaption.setOnClickListener(null)
    largeCaptionOverlay.setOnClickListener(null)
    viewModel.setIsDisplayingCaptionOverlay(false)
    sharedViewModel.setIsChildScrolling(false)
  }

  private fun presentFrom(from: TextView, storyPost: StoryPost) {
    val name = if (storyPost.sender.isSelf) {
      getString(R.string.StoryViewerPageFragment__you)
    } else {
      storyPost.sender.getDisplayName(requireContext())
    }

    val text = if (storyPost.group != null) {
      getString(R.string.StoryViewerPageFragment__s_to_s, name, storyPost.group.getDisplayName(requireContext()))
    } else {
      name
    }

    if (from.text != text) {
      from.text = text
    }

    from.setOnClickListener { onSenderClicked(storyPost.sender.id) }
  }

  private fun presentDate(date: TextView, storyPost: StoryPost) {
    val formattedDate = DateUtils.getBriefRelativeTimeSpanString(requireContext(), Locale.getDefault(), storyPost.dateInMilliseconds)
    if (date.text != formattedDate) {
      date.text = formattedDate
    }
  }

  private fun presentSenderAvatar(senderAvatar: AvatarImageView, post: StoryPost) {
    AvatarUtil.loadIconIntoImageView(post.sender, senderAvatar, DimensionUnit.DP.toPixels(32f).toInt())
    senderAvatar.setOnClickListener { onSenderClicked(post.sender.id) }
  }

  private fun presentGroupAvatar(groupAvatar: AvatarImageView, post: StoryPost) {
    if (post.group != null) {
      groupAvatar.setRecipient(post.group)
      groupAvatar.visible = true
      groupAvatar.setOnClickListener { onSenderClicked(post.sender.id) }
    } else {
      groupAvatar.visible = false
      groupAvatar.setOnClickListener(null)
    }
  }

  private fun onSenderClicked(senderId: RecipientId) {
    viewModel.setIsDisplayingRecipientBottomSheet(true)
    RecipientBottomSheetDialogFragment
      .show(childFragmentManager, senderId, null)
  }

  private fun presentBottomBar(post: StoryPost, replyState: StoryViewerPageState.ReplyState, isReceiptsEnabled: Boolean) {
    if (replyState == StoryViewerPageState.ReplyState.NONE) {
      viewsAndReplies.visible = false
      return
    } else {
      viewsAndReplies.visible = true
    }

    sendingBar.visible = false
    viewsAndReplies.isEnabled = true
    viewsAndReplies.iconTint = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.signal_colorOnSurface))

    when (replyState) {
      StoryViewerPageState.ReplyState.SENDING -> presentSendingBottomBar()
      StoryViewerPageState.ReplyState.PARTIAL_SEND -> presentPartialSendBottomBar()
      StoryViewerPageState.ReplyState.SEND_FAILURE -> presentSendFailureBottomBar()
      else -> presentViewsAndRepliesBottomBar(post, isReceiptsEnabled)
    }
  }

  private fun presentSendingBottomBar() {
    if (sendingProgressDrawable == null) {
      sendingProgressDrawable = IndeterminateDrawable.createCircularDrawable(
        requireContext(),
        CircularProgressIndicatorSpec(requireContext(), null).apply {
          indicatorSize = 18.dp
          indicatorInset = 2.dp
          trackColor = ContextCompat.getColor(requireContext(), R.color.transparent_white_40)
          indicatorColors = intArrayOf(ContextCompat.getColor(requireContext(), R.color.signal_dark_colorNeutralInverse))
          trackThickness = 2.dp
        }
      ).apply {
        setBounds(0, 0, 20.dp, 20.dp)
      }
    }

    sendingBarTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(
      sendingProgressDrawable,
      null,
      null,
      null
    )

    sendingBar.visible = true
    viewsAndReplies.isEnabled = false
  }

  private fun presentPartialSendBottomBar() {
    viewsAndReplies.setIconResource(R.drawable.symbol_error_circle_24)
    viewsAndReplies.iconTint = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.signal_light_colorError))
    viewsAndReplies.iconSize = 20.dp
    viewsAndReplies.setText(R.string.StoryViewerPageFragment__partially_sent)
  }

  private fun presentSendFailureBottomBar() {
    viewsAndReplies.setIconResource(R.drawable.symbol_error_circle_24)
    viewsAndReplies.iconTint = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.signal_light_colorError))
    viewsAndReplies.iconSize = 20.dp
    viewsAndReplies.setText(R.string.StoryViewerPageFragment__send_failed)
  }

  private fun presentViewsAndRepliesBottomBar(post: StoryPost, isReceiptsEnabled: Boolean) {
    val views = resources.getQuantityString(R.plurals.StoryViewerFragment__d_views, post.viewCount, post.viewCount)
    val replies = resources.getQuantityString(R.plurals.StoryViewerFragment__d_replies, post.replyCount, post.replyCount)

    if (Recipient.self() == post.sender) {
      if (isReceiptsEnabled) {
        if (post.replyCount == 0) {
          viewsAndReplies.setIconResource(R.drawable.symbol_chevron_right_compact_bold_16)
          viewsAndReplies.iconSize = DimensionUnit.DP.toPixels(16f).toInt()
          viewsAndReplies.iconGravity = MaterialButton.ICON_GRAVITY_TEXT_END
          viewsAndReplies.text = views
        } else {
          viewsAndReplies.setIconResource(R.drawable.symbol_chevron_right_compact_bold_16)
          viewsAndReplies.iconSize = DimensionUnit.DP.toPixels(16f).toInt()
          viewsAndReplies.iconGravity = MaterialButton.ICON_GRAVITY_TEXT_END
          viewsAndReplies.text = getString(R.string.StoryViewerFragment__s_s, views, replies)
        }
      } else {
        if (post.replyCount == 0) {
          viewsAndReplies.icon = null
          viewsAndReplies.setText(R.string.StoryViewerPageFragment__views_off)
        } else {
          viewsAndReplies.setIconResource(R.drawable.symbol_chevron_right_compact_bold_16)
          viewsAndReplies.iconSize = DimensionUnit.DP.toPixels(16f).toInt()
          viewsAndReplies.iconGravity = MaterialButton.ICON_GRAVITY_TEXT_END
          viewsAndReplies.text = replies
        }
      }
    } else if (post.replyCount > 0) {
      viewsAndReplies.setIconResource(R.drawable.symbol_chevron_right_compact_bold_16)
      viewsAndReplies.iconSize = DimensionUnit.DP.toPixels(16f).toInt()
      viewsAndReplies.iconGravity = MaterialButton.ICON_GRAVITY_TEXT_END
      viewsAndReplies.text = replies
    } else if (post.group != null) {
      viewsAndReplies.setIconResource(R.drawable.symbol_reply_24)
      viewsAndReplies.iconSize = DimensionUnit.DP.toPixels(20f).toInt()
      viewsAndReplies.iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
      viewsAndReplies.setText(R.string.StoryViewerPageFragment__reply_to_group)
    } else {
      viewsAndReplies.setIconResource(R.drawable.symbol_reply_24)
      viewsAndReplies.iconSize = DimensionUnit.DP.toPixels(20f).toInt()
      viewsAndReplies.iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
      viewsAndReplies.setText(R.string.StoryViewerPageFragment__reply)
    }
  }

  override fun setIsDisplayingLinkPreviewTooltip(isDisplayingLinkPreviewTooltip: Boolean) {
    viewModel.setIsDisplayingLinkPreviewTooltip(isDisplayingLinkPreviewTooltip)
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
          storyPost.conversationMessage.multiselectCollection.toSet()
        ) {
          MultiselectForwardFragment.showBottomSheet(childFragmentManager, it)
        }
      },
      onGoToChat = {
        lifecycleDisposable += ConversationIntents.createBuilder(requireContext(), storyViewerPageArgs.recipientId, -1L).subscribeBy {
          startActivity(it.build())
        }
      },
      onHide = {
        viewModel.setIsDisplayingHideDialog(true)
        StoryDialogs.hideStory(requireContext(), Recipient.resolved(storyViewerPageArgs.recipientId).getDisplayName(requireContext()), { viewModel.setIsDisplayingHideDialog(false) }) {
          lifecycleDisposable += viewModel.hideStory().subscribe {
            callback.onStoryHidden(storyViewerPageArgs.recipientId)
          }
        }
      },
      onUnhide = {
        lifecycleDisposable += viewModel.unhideStory().subscribe {
          Snackbar
            .make(requireView(), R.string.StoryViewerPageFragment__story_no_longer_hidden, Snackbar.LENGTH_SHORT)
            .show()
        }
      },
      onShare = {
        StoryContextMenu.share(this, it.conversationMessage.messageRecord as MmsMessageRecord)
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
      },
      onInfo = {
        showInfo(it)
      }
    )
  }

  class SingleTapHandler(
    private val container: View,
    private val onGoToNext: () -> Unit,
    private val onGoToPrevious: () -> Unit
  ) {

    companion object {
      private const val BOUNDARY_NEXT = 0.80f
      private const val BOUNDARY_PREV = 1f - BOUNDARY_NEXT
    }

    fun onActionUp(e: MotionEvent) {
      if (e.x < container.measuredWidth * getLeftBoundary()) {
        onGoToPrevious()
      } else if (e.x > container.measuredWidth - (container.measuredWidth * getRightBoundary())) {
        onGoToNext()
      }
    }

    private fun getLeftBoundary(): Float {
      return if (container.layoutDirection == View.LAYOUT_DIRECTION_LTR) {
        BOUNDARY_PREV
      } else {
        BOUNDARY_NEXT
      }
    }

    private fun getRightBoundary(): Float {
      return if (container.layoutDirection == View.LAYOUT_DIRECTION_LTR) {
        BOUNDARY_NEXT
      } else {
        BOUNDARY_PREV
      }
    }
  }

  companion object {
    private val TAG = Log.tag(StoryViewerPageFragment::class.java)

    private val MAX_VIDEO_PLAYBACK_DURATION: Long = TimeUnit.SECONDS.toMillis(30)
    private val MIN_GIF_LOOPS: Long = 3L
    private val MIN_GIF_PLAYBACK_DURATION = TimeUnit.SECONDS.toMillis(5)
    private val MIN_TEXT_STORY_PLAYBACK = TimeUnit.SECONDS.toMillis(3)
    private val CHARACTERS_PER_SECOND = 15L
    private val DEFAULT_DURATION = TimeUnit.SECONDS.toMillis(5)
    private val ONBOARDING_DURATION = TimeUnit.SECONDS.toMillis(10)

    private const val ARGS = "args"

    fun create(args: StoryViewerPageArgs): Fragment {
      return StoryViewerPageFragment().apply {
        arguments = bundleOf(
          ARGS to args
        )
      }
    }
  }

  private class StoryScaleListener(
    val viewModel: StoryViewerPageViewModel,
    val sharedViewModel: StoryViewerViewModel,
    val card: View
  ) : ScaleGestureDetector.SimpleOnScaleGestureListener() {

    private var scaleFactor = 1f

    var isPerformingEndAnimation: Boolean = false
      private set

    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
      viewModel.setIsUserScaling(true)
      sharedViewModel.setIsChildScrolling(true)
      card.animate().cancel()
      card.apply {
        pivotX = detector.focusX
        pivotY = detector.focusY
      }

      return true
    }

    override fun onScale(detector: ScaleGestureDetector): Boolean {
      scaleFactor *= detector.scaleFactor

      card.apply {
        scaleX = max(scaleFactor, 1f)
        scaleY = max(scaleFactor, 1f)
      }

      return true
    }

    override fun onScaleEnd(detector: ScaleGestureDetector) {
      scaleFactor = 1f
      isPerformingEndAnimation = true
      card.animate().scaleX(1f).scaleY(1f).setListener(object : AnimationCompleteListener() {
        override fun onAnimationEnd(animation: Animator) {
          isPerformingEndAnimation = false
          viewModel.setIsUserScaling(false)
          sharedViewModel.setIsChildScrolling(false)
        }
      })
    }
  }

  private class StoryGestureListener(
    private val container: View,
    private val singleTapHandler: SingleTapHandler,
    private val onReplyToPost: () -> Unit,
    private val onContentTranslation: (Float, Float) -> Unit,
    private val viewToTranslate: View = container.parent as View,
    private val sharedViewModel: StoryViewerViewModel
  ) : GestureDetector.SimpleOnGestureListener() {

    companion object {
      val INTERPOLATOR: Interpolator = PathInterpolatorCompat.create(0.4f, 0f, 0.2f, 1f)
    }

    private val maxSlide = DimensionUnit.DP.toPixels(56f * 2)

    override fun onDown(e: MotionEvent): Boolean {
      return true
    }

    override fun onSingleTapUp(e: MotionEvent): Boolean {
      singleTapHandler.onActionUp(e)
      return true
    }

    override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
      val isFirstStory = sharedViewModel.stateSnapshot.page == 0
      val isLastStory = sharedViewModel.stateSnapshot.pages.lastIndex == sharedViewModel.stateSnapshot.page
      val isXMagnitudeGreaterThanYMagnitude = abs(distanceX) > abs(distanceY) || viewToTranslate.translationX > 0f
      val isFirstAndHasYTranslationOrNegativeY = isFirstStory && (viewToTranslate.translationY > 0f || distanceY < 0f)
      val isLastAndHasYTranslationOrNegativeY = isLastStory && (viewToTranslate.translationY < 0f || distanceY > 0f)

      sharedViewModel.setIsChildScrolling(isXMagnitudeGreaterThanYMagnitude || isFirstAndHasYTranslationOrNegativeY || isLastAndHasYTranslationOrNegativeY)
      if (isFirstStory) {
        val delta = max(0f, (e2.rawY - (e1?.rawY ?: 0f))) / 3f
        val percent = INTERPOLATOR.getInterpolation(delta / maxSlide)
        val distance = maxSlide * percent

        viewToTranslate.animate().cancel()
        viewToTranslate.translationY = distance
      }

      if (isLastStory) {
        val delta = max(0f, ((e1?.rawY ?: 0f) - e2.rawY)) / 3f
        val percent = -INTERPOLATOR.getInterpolation(delta / maxSlide)
        val distance = maxSlide * percent

        viewToTranslate.animate().cancel()
        viewToTranslate.translationY = distance
      }

      val delta = max(0f, (e2.rawX - (e1?.rawX ?: 0f))) / 3f
      val percent = INTERPOLATOR.getInterpolation(delta / maxSlide)
      val distance = maxSlide * percent

      viewToTranslate.animate().cancel()
      viewToTranslate.translationX = distance

      onContentTranslation(viewToTranslate.translationX, viewToTranslate.translationY)

      return true
    }

    override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
      val isSideSwipe = abs(velocityX) > abs(velocityY)
      if (!isSideSwipe) {
        return false
      }

      if (viewToTranslate.translationX != 0f || viewToTranslate.translationY != 0f) {
        return false
      }

      if (ViewUtil.isLtr(container)) {
        if (velocityX < 0) {
          onReplyToPost()
        }
      } else if (velocityX > 0) {
        onReplyToPost()
      }

      return true
    }
  }

  override fun onContentReady() {
    Log.d(TAG, "Story content is ready.")
    sharedViewModel.setContentIsReady()
  }

  override fun onContentNotAvailable() {
    Log.d(TAG, "Story content is not available.")
    sharedViewModel.setContentIsReady()
  }

  override fun onInfoSheetDismissed() {
    viewModel.setIsDisplayingInfoDialog(false)
  }

  override fun sendAnywayAfterSafetyNumberChangedInBottomSheet(destinations: List<ContactSearchKey.RecipientSearchKey>) {
    error("Not supported, we handed a message record to the bottom sheet.")
  }

  override fun onMessageResentAfterSafetyNumberChangeInBottomSheet() {
    viewModel.setIsDisplayingPartialSendDialog(false)
  }

  override fun onCanceled() {
    viewModel.setIsDisplayingPartialSendDialog(false)
  }

  override fun onRecipientBottomSheetDismissed() {
    viewModel.setIsDisplayingRecipientBottomSheet(false)
  }

  interface Callback {
    fun onGoToPreviousStory(recipientId: RecipientId)
    fun onFinishedPosts(recipientId: RecipientId)
    fun onStoryHidden(recipientId: RecipientId)
    fun onContentTranslation(x: Float, y: Float)
  }
}
