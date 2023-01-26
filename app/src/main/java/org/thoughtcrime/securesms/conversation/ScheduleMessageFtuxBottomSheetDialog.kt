package org.thoughtcrime.securesms.conversation

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentManager
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.FixedRoundedCornerBottomSheetDialogFragment
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.databinding.ScheduleMessageFtuxBottomSheetBinding
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.BottomSheetUtil
import org.thoughtcrime.securesms.util.ServiceUtil

class ScheduleMessageFtuxBottomSheetDialog : FixedRoundedCornerBottomSheetDialogFragment() {
  override val peekHeightPercentage: Float = 0.66f
  override val themeResId: Int = R.style.Widget_Signal_FixedRoundedCorners_Messages

  private val binding by ViewBinderDelegate(ScheduleMessageFtuxBottomSheetBinding::bind)

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    return inflater.inflate(R.layout.schedule_message_ftux_bottom_sheet, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    if (Build.VERSION.SDK_INT >= 31 && !ServiceUtil.getAlarmManager(context).canScheduleExactAlarms()) {
      binding.reenableSettings.visibility = View.VISIBLE
      binding.okay.visibility = View.GONE
      val launcher: ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) {
          dismissAllowingStateLoss()
        }
      }
      binding.enableScheduledMessagesGoToSettings.setOnClickListener {
        SignalStore.uiHints().markHasSeenScheduledMessagesInfoSheet()
        launcher.launch(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + requireContext().packageName)))
        dismiss()
      }
    }
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
