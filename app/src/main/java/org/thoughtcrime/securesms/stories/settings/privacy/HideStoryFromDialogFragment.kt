package org.thoughtcrime.securesms.stories.settings.privacy

import android.os.Bundle
import android.view.View
import androidx.fragment.app.DialogFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.stories.settings.select.BaseStoryRecipientSelectionFragment

/**
 * Embeds HideStoryFromFragment in a full-screen dialog.
 */
class HideStoryFromDialogFragment : DialogFragment(R.layout.fragment_container), BaseStoryRecipientSelectionFragment.Callback {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setStyle(STYLE_NO_FRAME, R.style.Signal_DayNight_Dialog_FullScreen)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    // TODO [stories] replace with new bottom sheet
  }

  override fun exitFlow() {
    dismissAllowingStateLoss()
  }
}
