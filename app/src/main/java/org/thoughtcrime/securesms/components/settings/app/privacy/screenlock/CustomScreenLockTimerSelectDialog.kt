package org.thoughtcrime.securesms.components.settings.app.privacy.screenlock

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.privacy.expire.CustomExpireTimerSelectorView

/**
 * Dialog for selecting a custom timer value when setting the screen lock timeout.
 */
class CustomScreenLockTimerSelectDialog : DialogFragment() {

  private val viewModel: ScreenLockSettingsViewModel by activityViewModels()
  private lateinit var selector: CustomExpireTimerSelectorView

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val dialogView: View = LayoutInflater.from(context).inflate(R.layout.custom_expire_timer_select_dialog, null, false)

    selector = dialogView.findViewById(R.id.custom_expire_timer_select_dialog_selector)
    selector.setUnits(1, 3, R.array.CustomScreenLockTimerSelectorView__unit_labels)
    selector.setTimer(viewModel.state.value.screenLockActivityTimeout.toInt())

    return MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.ExpireTimerSettingsFragment__custom_time)
      .setView(dialogView)
      .setPositiveButton(R.string.ExpireTimerSettingsFragment__set) { _, _ ->
        viewModel.setScreenLockTimeout(selector.getTimer().toLong())
      }
      .setNegativeButton(android.R.string.cancel, null)
      .create()
  }
}
