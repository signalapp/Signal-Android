package org.thoughtcrime.securesms.stories.viewer.reply.group

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.FixedRoundedCornerBottomSheetDialogFragment
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.stories.viewer.page.StoryViewerPageViewModel
import org.thoughtcrime.securesms.stories.viewer.reply.BottomSheetBehaviorDelegate
import org.thoughtcrime.securesms.util.LifecycleDisposable

/**
 * Wraps a StoryGroupReplyFragment in a BottomSheetDialog
 */
class StoryGroupReplyBottomSheetDialogFragment : FixedRoundedCornerBottomSheetDialogFragment(), StoryGroupReplyFragment.Callback {

  override val themeResId: Int
    get() = R.style.Widget_Signal_FixedRoundedCorners_Stories

  private val storyId: Long
    get() = requireArguments().getLong(ARG_STORY_ID)

  private val groupRecipientId: RecipientId
    get() = requireArguments().getParcelable(ARG_GROUP_RECIPIENT_ID)!!

  override val peekHeightPercentage: Float = 1f

  private val lifecycleDisposable = LifecycleDisposable()

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
        .replace(R.id.fragment_container, StoryGroupReplyFragment.create(storyId, groupRecipientId))
        .commitAllowingStateLoss()
    }

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
  }

  override fun onDismiss(dialog: DialogInterface) {
    super.onDismiss(dialog)
    storyViewerPageViewModel.setIsDisplayingViewsAndRepliesDialog(false)
  }

  override fun onStartDirectReply(recipientId: RecipientId) {
    dismiss()
    storyViewerPageViewModel.startDirectReply(storyId, recipientId)
  }

  companion object {
    private const val ARG_STORY_ID = "arg.story.id"
    private const val ARG_GROUP_RECIPIENT_ID = "arg.group.recipient.id"

    fun create(storyId: Long, groupRecipientId: RecipientId): DialogFragment {
      return StoryGroupReplyBottomSheetDialogFragment().apply {
        arguments = Bundle().apply {
          putLong(ARG_STORY_ID, storyId)
          putParcelable(ARG_GROUP_RECIPIENT_ID, groupRecipientId)
        }
      }
    }
  }
}
