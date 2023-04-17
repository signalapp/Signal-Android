package org.thoughtcrime.securesms.stories.viewer.reply.group

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.core.util.getParcelableCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.FixedRoundedCornerBottomSheetDialogFragment
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.stories.viewer.page.StoryViewerPageViewModel
import org.thoughtcrime.securesms.stories.viewer.reply.BottomSheetBehaviorDelegate
import org.thoughtcrime.securesms.stories.viewer.reply.reaction.OnReactionSentView
import org.thoughtcrime.securesms.util.BottomSheetUtil.requireCoordinatorLayout
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Wraps a StoryGroupReplyFragment in a BottomSheetDialog
 */
class StoryGroupReplyBottomSheetDialogFragment : FixedRoundedCornerBottomSheetDialogFragment(), StoryGroupReplyFragment.Callback {

  override val themeResId: Int
    get() = R.style.Widget_Signal_FixedRoundedCorners_Stories

  private val storyId: Long
    get() = requireArguments().getLong(ARG_STORY_ID)

  private val groupRecipientId: RecipientId
    get() = requireArguments().getParcelableCompat(ARG_GROUP_RECIPIENT_ID, RecipientId::class.java)!!

  private val isFromNotification: Boolean
    get() = requireArguments().getBoolean(ARG_IS_FROM_NOTIFICATION, false)

  private val groupReplyStartPosition: Int
    get() = requireArguments().getInt(ARG_GROUP_REPLY_START_POSITION, -1)

  override val peekHeightPercentage: Float = 1f

  private val lifecycleDisposable = LifecycleDisposable()
  private var shouldShowFullScreen = false
  private var initialParentHeight = 0

  private lateinit var reactionView: OnReactionSentView

  private val storyViewerPageViewModel: StoryViewerPageViewModel by viewModels(
    ownerProducer = { requireParentFragment() }
  )

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    return inflater.inflate(R.layout.bottom_sheet_container, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    lifecycleDisposable.bindTo(viewLifecycleOwner)
    if (savedInstanceState == null) {
      childFragmentManager.beginTransaction()
        .replace(R.id.fragment_container, StoryGroupReplyFragment.create(storyId, groupRecipientId, isFromNotification, groupReplyStartPosition))
        .commitAllowingStateLoss()
    }

    reactionView = OnReactionSentView(requireContext())
    val container = view.rootView.findViewById<FrameLayout>(R.id.container)
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

  companion object {
    private const val ARG_STORY_ID = "arg.story.id"
    private const val ARG_GROUP_RECIPIENT_ID = "arg.group.recipient.id"
    private const val ARG_IS_FROM_NOTIFICATION = "is_from_notification"
    private const val ARG_GROUP_REPLY_START_POSITION = "group_reply_start_position"

    fun create(storyId: Long, groupRecipientId: RecipientId, isFromNotification: Boolean, groupReplyStartPosition: Int): DialogFragment {
      return StoryGroupReplyBottomSheetDialogFragment().apply {
        arguments = Bundle().apply {
          putLong(ARG_STORY_ID, storyId)
          putParcelable(ARG_GROUP_RECIPIENT_ID, groupRecipientId)
          putBoolean(ARG_IS_FROM_NOTIFICATION, isFromNotification)
          putInt(ARG_GROUP_REPLY_START_POSITION, groupReplyStartPosition)
        }
      }
    }
  }
}
