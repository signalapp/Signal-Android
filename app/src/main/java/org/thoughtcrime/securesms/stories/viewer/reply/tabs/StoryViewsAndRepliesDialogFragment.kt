package org.thoughtcrime.securesms.stories.viewer.reply.tabs

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.core.util.getParcelableCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.FixedRoundedCornerBottomSheetDialogFragment
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.stories.viewer.page.StoryViewerPageViewModel
import org.thoughtcrime.securesms.stories.viewer.reply.BottomSheetBehaviorDelegate
import org.thoughtcrime.securesms.stories.viewer.reply.StoryViewsAndRepliesPagerChild
import org.thoughtcrime.securesms.stories.viewer.reply.StoryViewsAndRepliesPagerParent
import org.thoughtcrime.securesms.stories.viewer.reply.group.StoryGroupReplyFragment
import org.thoughtcrime.securesms.stories.viewer.reply.reaction.OnReactionSentView
import org.thoughtcrime.securesms.util.BottomSheetUtil.requireCoordinatorLayout
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Tab based host for Views and Replies
 */
class StoryViewsAndRepliesDialogFragment : FixedRoundedCornerBottomSheetDialogFragment(), StoryViewsAndRepliesPagerParent, StoryGroupReplyFragment.Callback {

  override val themeResId: Int
    get() = R.style.Widget_Signal_FixedRoundedCorners_Stories

  private val storyId: Long
    get() = requireArguments().getLong(ARG_STORY_ID)

  private val groupRecipientId: RecipientId
    get() = requireArguments().getParcelableCompat(ARG_GROUP_RECIPIENT_ID, RecipientId::class.java)!!

  private val startPageIndex: Int
    get() = requireArguments().getInt(ARG_START_PAGE)

  private val isFromNotification: Boolean
    get() = requireArguments().getBoolean(ARG_IS_FROM_NOTIFICATION, false)

  private val groupReplyStartPosition: Int
    get() = requireArguments().getInt(ARG_GROUP_REPLY_START_POSITION, -1)

  override val peekHeightPercentage: Float = 1f

  private lateinit var pager: ViewPager2

  private var shouldShowFullScreen = false
  private var initialParentHeight = 0

  private val storyViewerPageViewModel: StoryViewerPageViewModel by viewModels(
    ownerProducer = { requireParentFragment() }
  )

  override val selectedChild: StoryViewsAndRepliesPagerParent.Child
    get() = StoryViewsAndRepliesPagerParent.Child.forIndex(pager.currentItem)

  private val onPageChangeCallback = PageChangeCallback()
  private val lifecycleDisposable = LifecycleDisposable()

  private lateinit var reactionView: OnReactionSentView

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    return inflater.inflate(R.layout.stories_views_and_replies_fragment, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    pager = view.findViewById(R.id.pager)

    reactionView = OnReactionSentView(requireContext())
    val container = pager.rootView.findViewById<FrameLayout>(R.id.container)
    container.addView(reactionView)

    val bottomSheetBehavior = (requireDialog() as BottomSheetDialog).behavior
    bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
      override fun onStateChanged(bottomSheet: View, newState: Int) = Unit

      override fun onSlide(bottomSheet: View, slideOffset: Float) {
        childFragmentManager.fragments.forEach {
          if (it is BottomSheetBehaviorDelegate) {
            it.onSlide(bottomSheet)
          }
        }
      }
    })

    val tabs: TabLayout = view.findViewById(R.id.tab_layout)

    ViewCompat.setNestedScrollingEnabled(tabs, false)
    pager.adapter = StoryViewsAndRepliesPagerAdapter(this, storyId, groupRecipientId, isFromNotification, groupReplyStartPosition)
    pager.setCurrentItem(startPageIndex, false)

    TabLayoutMediator(tabs, pager) { tab, position ->
      when (position) {
        0 -> tab.setText(R.string.StoryViewsAndRepliesDialogFragment__views)
        1 -> tab.setText(R.string.StoryViewsAndRepliesDialogFragment__replies)
      }
    }.attach()

    lifecycleDisposable.bindTo(viewLifecycleOwner)

    view.viewTreeObserver.addOnGlobalLayoutListener {
      val parentHeight = requireCoordinatorLayout().height
      val desiredHeight = (resources.displayMetrics.heightPixels * 0.6f).roundToInt()

      if (initialParentHeight == 0) {
        initialParentHeight = parentHeight
      }

      val targetHeight = when {
        parentHeight == 0 -> desiredHeight
        shouldShowFullScreen || parentHeight != initialParentHeight -> parentHeight
        else -> min(parentHeight, desiredHeight)
      }

      if (view.height != targetHeight) {
        view.updateLayoutParams {
          height = targetHeight
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    pager.registerOnPageChangeCallback(onPageChangeCallback)
  }

  override fun onPause() {
    super.onPause()
    pager.unregisterOnPageChangeCallback(onPageChangeCallback)
  }

  override fun onDismiss(dialog: DialogInterface) {
    super.onDismiss(dialog)
    storyViewerPageViewModel.setIsDisplayingViewsAndRepliesDialog(false)
  }

  override fun onStartDirectReply(recipientId: RecipientId) {
    dismiss()
    storyViewerPageViewModel.startDirectReply(storyId, recipientId)
  }

  override fun requestFullScreen(fullscreen: Boolean) {
    shouldShowFullScreen = fullscreen
    requireView().invalidate()
  }

  override fun onReactionEmojiSelected(emoji: String) {
    reactionView.playForEmoji(emoji)
  }

  private inner class PageChangeCallback : ViewPager2.OnPageChangeCallback() {
    override fun onPageScrollStateChanged(state: Int) {
      if (state == ViewPager2.SCROLL_STATE_IDLE) {
        pager.requestLayout()
      }
    }

    override fun onPageSelected(position: Int) {
      pager.post {
        childFragmentManager.fragments.forEach {
          if (it is StoryViewsAndRepliesPagerChild) {
            it.onPageSelected(StoryViewsAndRepliesPagerParent.Child.forIndex(position))
          }
          if (it is BottomSheetBehaviorDelegate) {
            it.onSlide(requireView().parent as View)
          }
        }
      }
    }
  }

  companion object {
    private const val ARG_STORY_ID = "arg.story.id"
    private const val ARG_START_PAGE = "arg.start.page"
    private const val ARG_GROUP_RECIPIENT_ID = "arg.group.recipient.id"
    private const val ARG_IS_FROM_NOTIFICATION = "is_from_notification"
    private const val ARG_GROUP_REPLY_START_POSITION = "group_reply_start_position"

    fun create(storyId: Long, groupRecipientId: RecipientId, startPage: StartPage, isFromNotification: Boolean, groupReplyStartPosition: Int): DialogFragment {
      return StoryViewsAndRepliesDialogFragment().apply {
        arguments = Bundle().apply {
          putLong(ARG_STORY_ID, storyId)
          putInt(ARG_START_PAGE, startPage.index)
          putParcelable(ARG_GROUP_RECIPIENT_ID, groupRecipientId)
          putBoolean(ARG_IS_FROM_NOTIFICATION, isFromNotification)
          putInt(ARG_GROUP_REPLY_START_POSITION, groupReplyStartPosition)
        }
      }
    }
  }

  enum class StartPage(val index: Int) {
    VIEWS(0),
    REPLIES(1)
  }
}
