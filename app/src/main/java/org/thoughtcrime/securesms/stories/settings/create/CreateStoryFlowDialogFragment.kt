package org.thoughtcrime.securesms.stories.settings.create

import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.stories.settings.select.BaseStoryRecipientSelectionFragment
import org.thoughtcrime.securesms.util.WindowUtil

class CreateStoryFlowDialogFragment : DialogFragment(R.layout.create_story_flow_dialog_fragment), BaseStoryRecipientSelectionFragment.Callback, CreateStoryWithViewersFragment.Callback {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setStyle(STYLE_NO_FRAME, R.style.Signal_DayNight_Dialog_FullScreen)
  }

  override fun exitFlow() {
    dismissAllowingStateLoss()
  }

  override fun onDone(recipientId: RecipientId) {
    setFragmentResult(
      CreateStoryWithViewersFragment.REQUEST_KEY,
      Bundle().apply {
        putParcelable(CreateStoryWithViewersFragment.STORY_RECIPIENT, recipientId)
      }
    )
    dismissAllowingStateLoss()
  }

  override fun setStatusBarColor(color: Int) {
    WindowUtil.setStatusBarColor(requireDialog().window!!, color)
  }
}
