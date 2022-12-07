package org.thoughtcrime.securesms.stories.viewer

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.viewpager2.widget.ViewPager2
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.stories.StoryViewerArgs
import org.thoughtcrime.securesms.stories.viewer.first.StoryFirstTimeNavigationFragment
import org.thoughtcrime.securesms.stories.viewer.page.StoryViewerPageArgs
import org.thoughtcrime.securesms.stories.viewer.page.StoryViewerPageFragment
import org.thoughtcrime.securesms.stories.viewer.reply.StoriesSharedElementCrossFaderView
import org.thoughtcrime.securesms.util.LifecycleDisposable

/**
 * Fragment which manages a vertical pager fragment of stories.
 */
class StoryViewerFragment :
  Fragment(R.layout.stories_viewer_fragment),
  StoryViewerPageFragment.Callback,
  StoriesSharedElementCrossFaderView.Callback {

  private val onPageChanged = OnPageChanged()

  private lateinit var storyPager: ViewPager2

  private val viewModel: StoryViewerViewModel by viewModels(
    factoryProducer = {
      StoryViewerViewModel.Factory(storyViewerArgs, StoryViewerRepository())
    }
  )

  private val lifecycleDisposable = LifecycleDisposable()

  private val storyViewerArgs: StoryViewerArgs by lazy { requireArguments().getParcelable(ARGS)!! }

  private lateinit var storyCrossfader: StoriesSharedElementCrossFaderView

  private var pagerOnPageSelectedLock: Boolean = false

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    storyCrossfader = view.findViewById(R.id.story_content_crossfader)
    storyPager = view.findViewById(R.id.story_item_pager)

    ViewCompat.setTransitionName(storyCrossfader, "story")
    storyCrossfader.callback = this

    val adapter = StoryViewerPagerAdapter(
      this,
      StoryViewerPageArgs(
        recipientId = Recipient.UNKNOWN.id,
        initialStoryId = storyViewerArgs.storyId,
        isJumpForwardToUnviewed = storyViewerArgs.isJumpToUnviewed,
        isOutgoingOnly = storyViewerArgs.isFromMyStories,
        source = when {
          storyViewerArgs.isFromInfoContextMenuAction -> StoryViewerPageArgs.Source.INFO_CONTEXT
          storyViewerArgs.isFromNotification -> StoryViewerPageArgs.Source.NOTIFICATION
          else -> StoryViewerPageArgs.Source.UNKNOWN
        },
        groupReplyStartPosition = storyViewerArgs.groupReplyStartPosition
      )
    )

    storyPager.adapter = adapter
    storyPager.overScrollMode = ViewPager2.OVER_SCROLL_NEVER

    lifecycleDisposable += viewModel.allowParentScrolling.observeOn(AndroidSchedulers.mainThread()).subscribe {
      storyPager.isUserInputEnabled = it
    }

    storyPager.offscreenPageLimit = 1

    lifecycleDisposable.bindTo(viewLifecycleOwner)
    lifecycleDisposable += viewModel.state.observeOn(AndroidSchedulers.mainThread()).subscribe { state ->
      if (state.noPosts) {
        ActivityCompat.finishAfterTransition(requireActivity())
      }

      adapter.setPages(state.pages)
      if (state.pages.isNotEmpty() && storyPager.currentItem != state.page) {
        pagerOnPageSelectedLock = true
        storyPager.isUserInputEnabled = false
        storyPager.setCurrentItem(state.page, state.previousPage > -1)
        pagerOnPageSelectedLock = false

        if (state.page >= state.pages.size) {
          ActivityCompat.finishAfterTransition(requireActivity())
          lifecycleDisposable.clear()
        }
      }

      when (state.crossfadeSource) {
        is StoryViewerState.CrossfadeSource.TextModel -> storyCrossfader.setSourceView(state.crossfadeSource.storyTextPostModel)
        is StoryViewerState.CrossfadeSource.ImageUri -> storyCrossfader.setSourceView(state.crossfadeSource.imageUri, state.crossfadeSource.imageBlur)
        StoryViewerState.CrossfadeSource.None -> Unit
      }

      if (state.crossfadeTarget is StoryViewerState.CrossfadeTarget.Record) {
        storyCrossfader.setTargetView(state.crossfadeTarget.messageRecord)
        requireActivity().supportStartPostponedEnterTransition()
      }

      if (state.skipCrossfade) {
        viewModel.setCrossfaderIsReady(true)
      }

      if (state.loadState.isReady()) {
        storyCrossfader.alpha = 0f
      }
    }

    if (savedInstanceState != null && savedInstanceState.containsKey(HIDDEN)) {
      val ids: List<RecipientId> = savedInstanceState.getParcelableArrayList(HIDDEN)!!
      viewModel.addHiddenAndRefresh(ids.toSet())
    } else {
      viewModel.refresh()

      if (!SignalStore.storyValues().userHasSeenFirstNavView) {
        StoryFirstTimeNavigationFragment().show(childFragmentManager, null)
      }
    }

    if (Build.VERSION.SDK_INT >= 31) {
      lifecycleDisposable += viewModel.isFirstTimeNavigationShowing.subscribe {
        if (it) {
          requireView().rootView.setRenderEffect(RenderEffect.createBlurEffect(100f, 100f, Shader.TileMode.CLAMP))
        } else {
          requireView().rootView.setRenderEffect(null)
        }
      }
    }
  }

  override fun onSaveInstanceState(outState: Bundle) {
    outState.putParcelableArrayList(HIDDEN, ArrayList(viewModel.getHidden()))
  }

  override fun onResume() {
    super.onResume()
    viewModel.setIsScrolling(false)
    storyPager.registerOnPageChangeCallback(onPageChanged)
  }

  override fun onPause() {
    super.onPause()
    viewModel.setIsScrolling(false)
    storyPager.unregisterOnPageChangeCallback(onPageChanged)
  }

  override fun onGoToPreviousStory(recipientId: RecipientId) {
    viewModel.onGoToPrevious(recipientId)
  }

  override fun onFinishedPosts(recipientId: RecipientId) {
    viewModel.onGoToNext(recipientId)
  }

  override fun onStoryHidden(recipientId: RecipientId) {
    viewModel.addHiddenAndRefresh(setOf(recipientId))
  }

  override fun onContentTranslation(x: Float, y: Float) {
    storyCrossfader.translationX = x
    storyCrossfader.translationY = y
  }

  override fun onReadyToAnimate() {
  }

  override fun onAnimationStarted() {
    viewModel.setCrossfaderIsReady(false)
  }

  override fun onAnimationFinished() {
    viewModel.setCrossfaderIsReady(true)
  }

  inner class OnPageChanged : ViewPager2.OnPageChangeCallback() {
    override fun onPageSelected(position: Int) {
      if (!pagerOnPageSelectedLock) {
        viewModel.setSelectedPage(position)
      }
    }

    override fun onPageScrollStateChanged(state: Int) {
      viewModel.setIsScrolling(state == ViewPager2.SCROLL_STATE_DRAGGING)
      if (state == ViewPager2.SCROLL_STATE_IDLE) {
        storyPager.isUserInputEnabled = true
      }
    }
  }

  companion object {
    private const val ARGS = "args"
    private const val HIDDEN = "hidden"

    fun create(storyViewerArgs: StoryViewerArgs): Fragment {
      return StoryViewerFragment().apply {
        arguments = Bundle().apply {
          putParcelable(ARGS, storyViewerArgs)
        }
      }
    }
  }
}
