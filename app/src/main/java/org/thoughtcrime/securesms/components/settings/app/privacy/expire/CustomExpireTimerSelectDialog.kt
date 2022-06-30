package org.thoughtcrime.securesms.components.settings.app.privacy.expire

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.thoughtcrime.securesms.R

/**
 * Dialog for selecting a custom expire timer value.
 */
class CustomExpireTimerSelectDialog : DialogFragment() {

  private lateinit var viewModel: ExpireTimerSettingsViewModel
  private lateinit var selector: CustomExpireTimerSelectorView

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val dialogView: View = LayoutInflater.from(context).inflate(R.layout.custom_expire_timer_select_dialog, null, false)
    selector = dialogView.findViewById(R.id.custom_expire_timer_select_dialog_selector)

    return MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.ExpireTimerSettingsFragment__custom_time)
      .setView(dialogView)
      .setPositiveButton(R.string.ExpireTimerSettingsFragment__set) { _, _ ->
        viewModel.select(selector.getTimer())
      }
      .setNegativeButton(android.R.string.cancel, null)
      .create()
  }

  @Suppress("DEPRECATION")
  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    viewModel = ViewModelProvider(NavHostFragment.findNavController(this).getViewModelStoreOwner(R.id.app_settings_expire_timer))
      .get(ExpireTimerSettingsViewModel::class.java)

    viewModel.state.observe(this) { selector.setTimer(it.currentTimer) }
  }

  companion object {
    private const val DIALOG_TAG = "CustomTimerSelectDialog"

    fun show(fragmentManager: FragmentManager) {
      CustomExpireTimerSelectDialog().show(fragmentManager, DIALOG_TAG)
    }
  }
}
