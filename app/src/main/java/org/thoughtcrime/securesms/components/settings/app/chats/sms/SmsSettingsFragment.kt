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
import androidx.navigation.fragment.findNavController
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.components.settings.models.OutlinedLearnMore
import org.thoughtcrime.securesms.exporter.flow.SmsExportActivity
import org.thoughtcrime.securesms.exporter.flow.SmsExportDialogs
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.keyvalue.SmsExportPhase
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
    OutlinedLearnMore.register(adapter)

    smsExportLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
      if (it.resultCode == Activity.RESULT_OK) {
        SmsExportDialogs.showSmsRemovalDialog(requireContext(), requireView())
      }
    }

    viewModel = ViewModelProvider(this)[SmsSettingsViewModel::class.java]

    viewModel.state.observe(viewLifecycleOwner) {
      adapter.submitList(getConfiguration(it).toMappingModelList())
    }
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    if (Util.isDefaultSmsProvider(requireContext())) {
      SignalStore.settings().setDefaultSms(true)
    } else {
      SignalStore.settings().setDefaultSms(false)
      if (SignalStore.misc().smsExportPhase.isAtLeastPhase1()) {
        findNavController().navigateUp()
      }
    }
  }

  private fun getConfiguration(state: SmsSettingsState): DSLConfiguration {
    return configure {

      if (state.useAsDefaultSmsApp && SignalStore.misc().smsExportPhase.isAtLeastPhase1()) {
        customPref(
          OutlinedLearnMore.Model(
            summary = DSLSettingsText.from(R.string.SmsSettingsFragment__sms_support_will_be_removed_soon_to_focus_on_encrypted_messaging),
            learnMoreUrl = getString(R.string.sms_export_url)
          )
        )
      }

      when (state.smsExportState) {
        SmsExportState.FETCHING -> Unit
        SmsExportState.HAS_UNEXPORTED_MESSAGES -> {
          clickPref(
            title = DSLSettingsText.from(R.string.SmsSettingsFragment__export_sms_messages),
            summary = DSLSettingsText.from(R.string.SmsSettingsFragment__you_can_export_your_sms_messages_to_your_phones_sms_database),
            onClick = {
              smsExportLauncher.launch(SmsExportActivity.createIntent(requireContext()))
            }
          )

          dividerPref()
        }
        SmsExportState.ALL_MESSAGES_EXPORTED -> {
          clickPref(
            title = DSLSettingsText.from(R.string.SmsSettingsFragment__remove_sms_messages),
            summary = DSLSettingsText.from(R.string.SmsSettingsFragment__remove_sms_messages_from_signal_to_clear_up_storage_space),
            onClick = {
              SmsExportDialogs.showSmsRemovalDialog(requireContext(), requireView())
            }
          )

          clickPref(
            title = DSLSettingsText.from(R.string.SmsSettingsFragment__export_sms_messages_again),
            summary = DSLSettingsText.from(R.string.SmsSettingsFragment__exporting_again_can_result_in_duplicate_messages),
            onClick = {
              SmsExportDialogs.showSmsReExportDialog(requireContext()) {
                smsExportLauncher.launch(SmsExportActivity.createIntent(requireContext(), isReExport = true))
              }
            }
          )

          dividerPref()
        }
        SmsExportState.NO_SMS_MESSAGES_IN_DATABASE -> Unit
        SmsExportState.NOT_AVAILABLE -> Unit
      }

      if (state.useAsDefaultSmsApp || SignalStore.misc().smsExportPhase == SmsExportPhase.PHASE_0) {
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
      }

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
}
