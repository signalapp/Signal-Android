package org.thoughtcrime.securesms.conversation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.FixedRoundedCornerBottomSheetDialogFragment
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.databinding.ScheduleMessageFtuxBottomSheetBinding
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.BottomSheetUtil

class ScheduleMessageFtuxBottomSheetDialog : FixedRoundedCornerBottomSheetDialogFragment() {
  override val peekHeightPercentage: Float = 0.66f
  override val themeResId: Int = R.style.Widget_Signal_FixedRoundedCorners_Messages

  private val binding by ViewBinderDelegate(ScheduleMessageFtuxBottomSheetBinding::bind)

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    return inflater.inflate(R.layout.schedule_message_ftux_bottom_sheet, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    binding.okay.setOnClickListener {
      SignalStore.uiHints().markHasSeenScheduledMessagesInfoSheet()
      dismiss()
    }
  }

  companion object {
    @JvmStatic
    fun show(fragmentManager: FragmentManager) {
      val fragment = ScheduleMessageFtuxBottomSheetDialog()

      fragment.show(fragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
    }
  }
}
