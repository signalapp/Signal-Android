package org.thoughtcrime.securesms.stories.viewer.views

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.FixedRoundedCornerBottomSheetDialogFragment
import org.thoughtcrime.securesms.stories.viewer.page.StoryViewerPageViewModel

/**
 * Wraps StoryViewsFragment in a BottomSheetDialog
 */
class StoryViewsBottomSheetDialogFragment : FixedRoundedCornerBottomSheetDialogFragment() {

  override val themeResId: Int
    get() = R.style.Widget_Signal_FixedRoundedCorners_Stories

  private val storyId: Long
    get() = requireArguments().getLong(ARG_STORY_ID)

  private val storyViewerPageViewModel: StoryViewerPageViewModel by viewModels(
    ownerProducer = { requireParentFragment() }
  )

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    return inflater.inflate(R.layout.bottom_sheet_container, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    if (savedInstanceState == null) {
      childFragmentManager.beginTransaction()
        .replace(R.id.fragment_container, StoryViewsFragment.create(storyId))
        .commitAllowingStateLoss()
    }
  }

  override fun onDismiss(dialog: DialogInterface) {
    super.onDismiss(dialog)
    storyViewerPageViewModel.setIsDisplayingViewsAndRepliesDialog(false)
  }

  companion object {
    private const val ARG_STORY_ID = "arg.story.id"

    fun create(storyId: Long): DialogFragment {
      return StoryViewsBottomSheetDialogFragment().apply {
        arguments = Bundle().apply {
          putLong(ARG_STORY_ID, storyId)
        }
      }
    }
  }
}
