package org.thoughtcrime.securesms.components.settings.app.chats.sms

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.Navigation
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import org.signal.core.util.concurrent.SignalExecutors
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.exporter.flow.SmsExportActivity
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.SmsUtil
import org.thoughtcrime.securesms.util.Util
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.navigation.safeNavigate

private const val SMS_REQUEST_CODE: Short = 1234

class SmsSettingsFragment : DSLSettingsFragment(R.string.preferences__sms_mms) {

  private lateinit var viewModel: SmsSettingsViewModel
  private lateinit var smsExportLauncher: ActivityResultLauncher<Intent>

  override fun onResume() {
    super.onResume()
    viewModel.checkSmsEnabled()
  }

  override fun bindAdapter(adapter: MappingAdapter) {
    smsExportLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
      if (it.resultCode == Activity.RESULT_OK) {
        showSmsRemovalDialog()
      }
    }

    viewModel = ViewModelProvider(this)[SmsSettingsViewModel::class.java]

    viewModel.state.observe(viewLifecycleOwner) {
      adapter.submitList(getConfiguration(it).toMappingModelList())
    }
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    SignalStore.settings().setDefaultSms(Util.isDefaultSmsProvider(requireContext()))
  }

  private fun getConfiguration(state: SmsSettingsState): DSLConfiguration {
    return configure {
      when (state.smsExportState) {
        SmsSettingsState.SmsExportState.FETCHING -> Unit
        SmsSettingsState.SmsExportState.HAS_UNEXPORTED_MESSAGES -> {
          clickPref(
            title = DSLSettingsText.from(R.string.SmsSettingsFragment__export_sms_messages),
            onClick = {
              smsExportLauncher.launch(SmsExportActivity.createIntent(requireContext()))
            }
          )

          dividerPref()
        }
        SmsSettingsState.SmsExportState.ALL_MESSAGES_EXPORTED -> {
          clickPref(
            title = DSLSettingsText.from(R.string.SmsSettingsFragment__remove_sms_messages),
            onClick = {
              showSmsRemovalDialog()
            }
          )

          dividerPref()
        }
        SmsSettingsState.SmsExportState.NO_SMS_MESSAGES_IN_DATABASE -> Unit
        SmsSettingsState.SmsExportState.NOT_AVAILABLE -> Unit
      }

      @Suppress("DEPRECATION")
      clickPref(
        title = DSLSettingsText.from(R.string.SmsSettingsFragment__use_as_default_sms_app),
        summary = DSLSettingsText.from(if (state.useAsDefaultSmsApp) R.string.arrays__enabled else R.string.arrays__disabled),
        onClick = {
          if (state.useAsDefaultSmsApp) {
            startDefaultAppSelectionIntent()
          } else {
            startActivityForResult(SmsUtil.getSmsRoleIntent(requireContext()), SMS_REQUEST_CODE.toInt())
          }
        }
      )

      switchPref(
        title = DSLSettingsText.from(R.string.preferences__sms_delivery_reports),
        summary = DSLSettingsText.from(R.string.preferences__request_a_delivery_report_for_each_sms_message_you_send),
        isChecked = state.smsDeliveryReportsEnabled,
        onClick = {
          viewModel.setSmsDeliveryReportsEnabled(!state.smsDeliveryReportsEnabled)
        }
      )

      switchPref(
        title = DSLSettingsText.from(R.string.preferences__support_wifi_calling),
        summary = DSLSettingsText.from(R.string.preferences__enable_if_your_device_supports_sms_mms_delivery_over_wifi),
        isChecked = state.wifiCallingCompatibilityEnabled,
        onClick = {
          viewModel.setWifiCallingCompatibilityEnabled(!state.wifiCallingCompatibilityEnabled)
        }
      )

      if (Build.VERSION.SDK_INT < 21) {
        clickPref(
          title = DSLSettingsText.from(R.string.preferences__advanced_mms_access_point_names),
          onClick = {
            Navigation.findNavController(requireView()).safeNavigate(R.id.action_smsSettingsFragment_to_mmsPreferencesFragment)
          }
        )
      }
    }
  }

  // Linter isn't smart enough to figure out the else only happens if API >= 24
  @SuppressLint("InlinedApi")
  @Suppress("DEPRECATION")
  private fun startDefaultAppSelectionIntent() {
    val intent: Intent = when {
      Build.VERSION.SDK_INT < 23 -> Intent(Settings.ACTION_WIRELESS_SETTINGS)
      Build.VERSION.SDK_INT < 24 -> Intent(Settings.ACTION_SETTINGS)
      else -> Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
    }

    startActivityForResult(intent, SMS_REQUEST_CODE.toInt())
  }

  private fun showSmsRemovalDialog() {
    MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.RemoveSmsMessagesDialogFragment__remove_sms_messages)
      .setMessage(R.string.RemoveSmsMessagesDialogFragment__you_have_changed)
      .setPositiveButton(R.string.RemoveSmsMessagesDialogFragment__keep_messages) { _, _ -> }
      .setNegativeButton(R.string.RemoveSmsMessagesDialogFragment__remove_messages) { _, _ ->
        SignalExecutors.BOUNDED.execute {
          SignalDatabase.sms.deleteExportedMessages()
          SignalDatabase.mms.deleteExportedMessages()
        }
        Snackbar.make(requireView(), R.string.SmsSettingsFragment__sms_messages_removed, Snackbar.LENGTH_SHORT).show()
      }
      .show()
  }
}
