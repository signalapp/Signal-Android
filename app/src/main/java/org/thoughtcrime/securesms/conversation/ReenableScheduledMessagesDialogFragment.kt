package org.thoughtcrime.securesms.conversation

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
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
import org.thoughtcrime.securesms.conversation.ScheduleMessageFtuxBottomSheetDialog.Companion.show
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.BottomSheetUtil
import org.thoughtcrime.securesms.util.ServiceUtil

/**
 * Bottom sheet dialog to prompt user to enable schedule alarms permission for scheduling messages
 */
class ReenableScheduledMessagesDialogFragment : FixedRoundedCornerBottomSheetDialogFragment() {

  override val peekHeightPercentage: Float = 1f

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    return inflater.inflate(R.layout.reenable_scheduled_messages_dialog_fragment, container, false)
  }

  @SuppressLint("InlinedApi")
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    val launcher: ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
      if (it.resultCode == Activity.RESULT_OK) {
        dismissAllowingStateLoss()
      }
    }

    view.findViewById<View>(R.id.reenable_scheduled_messages_go_to_settings).setOnClickListener {
      launcher.launch(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + requireContext().packageName)))
    }
  }

  companion object {
    @JvmStatic
    fun showIfNeeded(context: Context, fragmentManager: FragmentManager) {
      var hasPermission = true
      if (Build.VERSION.SDK_INT >= 31) {
        val alarmManager = ServiceUtil.getAlarmManager(context)
        hasPermission = alarmManager.canScheduleExactAlarms()
      }
      val fragment = if (!SignalStore.uiHints().hasSeenScheduledMessagesInfoSheet()) {
        ScheduleMessageFtuxBottomSheetDialog()
      } else if (!hasPermission) {
        ReenableScheduledMessagesDialogFragment()
      } else {
        null
      }
      fragment?.show(fragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
    }
  }
}
