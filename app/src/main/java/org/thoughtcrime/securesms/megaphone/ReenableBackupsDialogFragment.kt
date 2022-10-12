package org.thoughtcrime.securesms.megaphone

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.FixedRoundedCornerBottomSheetDialogFragment

/**
 * Bottom sheet dialog to prompt user to enable schedule alarms permission for triggering backups.
 */
class ReenableBackupsDialogFragment : FixedRoundedCornerBottomSheetDialogFragment() {

  override val peekHeightPercentage: Float = 1f

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    return inflater.inflate(R.layout.reenable_backups_dialog_fragment, container, false)
  }

  @SuppressLint("InlinedApi")
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    val launcher: ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
      if (it.resultCode == Activity.RESULT_OK) {
        dismissAllowingStateLoss()
      }
    }

    view.findViewById<View>(R.id.reenable_backups_go_to_settings).setOnClickListener {
      launcher.launch(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + requireContext().packageName)))
    }
  }
}
