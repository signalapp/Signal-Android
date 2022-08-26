package org.thoughtcrime.securesms.components.settings.app.account

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.text.InputType
import android.util.DisplayMetrics
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.autofill.HintConstants
import androidx.core.app.DialogCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.Navigation
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.contactshare.SimpleTextWatcher
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.lock.PinHashing
import org.thoughtcrime.securesms.lock.v2.CreateKbsPinActivity
import org.thoughtcrime.securesms.lock.v2.KbsConstants
import org.thoughtcrime.securesms.lock.v2.PinKeyboardType
import org.thoughtcrime.securesms.pin.RegistrationLockV2Dialog
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.navigation.safeNavigate

class AccountSettingsFragment : DSLSettingsFragment(R.string.AccountSettingsFragment__account) {

  lateinit var viewModel: AccountSettingsViewModel

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    if (requestCode == CreateKbsPinActivity.REQUEST_NEW_PIN && resultCode == CreateKbsPinActivity.RESULT_OK) {
      Snackbar.make(requireView(), R.string.ConfirmKbsPinFragment__pin_created, Snackbar.LENGTH_LONG).show()
    }
  }

  override fun onResume() {
    super.onResume()
    viewModel.refreshState()
  }

  override fun bindAdapter(adapter: MappingAdapter) {
    viewModel = ViewModelProvider(this)[AccountSettingsViewModel::class.java]

    viewModel.state.observe(viewLifecycleOwner) { state ->
      adapter.submitList(getConfiguration(state).toMappingModelList())
    }
  }

  private fun getConfiguration(state: AccountSettingsState): DSLConfiguration {
    return configure {

      sectionHeaderPref(R.string.preferences_app_protection__signal_pin)

      @Suppress("DEPRECATION")
      clickPref(
        title = DSLSettingsText.from(if (state.hasPin) R.string.preferences_app_protection__change_your_pin else R.string.preferences_app_protection__create_a_pin),
        onClick = {
          if (state.hasPin) {
            startActivityForResult(CreateKbsPinActivity.getIntentForPinChangeFromSettings(requireContext()), CreateKbsPinActivity.REQUEST_NEW_PIN)
          } else {
            startActivityForResult(CreateKbsPinActivity.getIntentForPinCreate(requireContext()), CreateKbsPinActivity.REQUEST_NEW_PIN)
          }
        }
      )

      switchPref(
        title = DSLSettingsText.from(R.string.preferences_app_protection__pin_reminders),
        summary = DSLSettingsText.from(R.string.AccountSettingsFragment__youll_be_asked_less_frequently),
        isChecked = state.hasPin && state.pinRemindersEnabled,
        isEnabled = state.hasPin,
        onClick = {
          setPinRemindersEnabled(!state.pinRemindersEnabled)
        }
      )

      switchPref(
        title = DSLSettingsText.from(R.string.preferences_app_protection__registration_lock),
        summary = DSLSettingsText.from(R.string.AccountSettingsFragment__require_your_signal_pin),
        isChecked = state.registrationLockEnabled,
        isEnabled = state.hasPin,
        onClick = {
          setRegistrationLockEnabled(!state.registrationLockEnabled)
        }
      )

      clickPref(
        title = DSLSettingsText.from(R.string.preferences__advanced_pin_settings),
        onClick = {
          Navigation.findNavController(requireView()).safeNavigate(R.id.action_accountSettingsFragment_to_advancedPinSettingsActivity)
        }
      )

      dividerPref()

      sectionHeaderPref(R.string.AccountSettingsFragment__account)

      if (Recipient.self().changeNumberCapability == Recipient.Capability.SUPPORTED && SignalStore.account().isRegistered) {
        clickPref(
          title = DSLSettingsText.from(R.string.AccountSettingsFragment__change_phone_number),
          onClick = {
            Navigation.findNavController(requireView()).safeNavigate(R.id.action_accountSettingsFragment_to_changePhoneNumberFragment)
          }
        )
      }

      clickPref(
        title = DSLSettingsText.from(R.string.preferences_chats__transfer_account),
        summary = DSLSettingsText.from(R.string.preferences_chats__transfer_account_to_a_new_android_device),
        onClick = {
          Navigation.findNavController(requireView()).safeNavigate(R.id.action_accountSettingsFragment_to_oldDeviceTransferActivity)
        }
      )

      clickPref(
        title = DSLSettingsText.from(R.string.preferences__delete_account, ContextCompat.getColor(requireContext(), R.color.signal_alert_primary)),
        onClick = {
          Navigation.findNavController(requireView()).safeNavigate(R.id.action_accountSettingsFragment_to_deleteAccountFragment)
        }
      )
    }
  }

  private fun setRegistrationLockEnabled(enabled: Boolean) {
    if (enabled) {
      RegistrationLockV2Dialog.showEnableDialog(requireContext()) { viewModel.refreshState() }
    } else {
      RegistrationLockV2Dialog.showDisableDialog(requireContext()) { viewModel.refreshState() }
    }
  }

  private fun setPinRemindersEnabled(enabled: Boolean) {
    SignalStore.pinValues().setPinRemindersEnabled(enabled)
    viewModel.refreshState()
  }
}
