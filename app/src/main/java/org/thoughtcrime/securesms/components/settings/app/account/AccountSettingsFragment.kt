package org.thoughtcrime.securesms.components.settings.app.account

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.Navigation
import com.google.android.material.snackbar.Snackbar
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.Mp03CustomDialog
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsAdapter
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
import org.thoughtcrime.securesms.util.ServiceUtil

class AccountSettingsFragment : DSLSettingsFragment(R.string.AccountSettingsFragment__account) {

  lateinit var viewModel: AccountSettingsViewModel

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    if (requestCode == CreateKbsPinActivity.REQUEST_NEW_PIN && resultCode == CreateKbsPinActivity.RESULT_OK) {
      Snackbar.make(requireView(), R.string.ConfirmKbsPinFragment__pin_created, Snackbar.LENGTH_LONG).setTextColor(Color.WHITE).show()
    }
  }

  override fun onResume() {
    super.onResume()
    viewModel.refreshState()
  }

  override fun bindAdapter(adapter: DSLSettingsAdapter) {
    viewModel = ViewModelProviders.of(this)[AccountSettingsViewModel::class.java]

    viewModel.state.observe(viewLifecycleOwner) { state ->
      adapter.submitList(getConfiguration(state).toMappingModelList())
    }
  }

  private fun getConfiguration(state: AccountSettingsState): DSLConfiguration {
    return configure {

      sectionHeaderPref(R.string.preferences_app_protection__signal_pin)

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
//        summary = DSLSettingsText.from(R.string.AccountSettingsFragment__youll_be_asked_less_frequently),
        isChecked = state.hasPin && state.pinRemindersEnabled,
        isEnabled = state.hasPin,
        onClick = {
          setPinRemindersEnabled(!state.pinRemindersEnabled)
        }
      )

      switchPref(
        title = DSLSettingsText.from(R.string.preferences_app_protection__registration_lock),
//        summary = DSLSettingsText.from(R.string.AccountSettingsFragment__require_your_signal_pin),
        isChecked = state.registrationLockEnabled,
        isEnabled = state.hasPin,
        onClick = {
          setRegistrationLockEnabled(!state.registrationLockEnabled)
        }
      )

      clickPref(
        title = DSLSettingsText.from(R.string.preferences__advanced_pin_settings),
        onClick = {
          Navigation.findNavController(requireView()).navigate(R.id.action_accountSettingsFragment_to_advancedPinSettingsActivity)
        }
      )

      sectionHeaderPref(R.string.AccountSettingsFragment__account)

      /*clickPref(
        title = DSLSettingsText.from(R.string.preferences_chats__transfer_account),
        summary = DSLSettingsText.from(R.string.preferences_chats__transfer_account_to_a_new_android_device),
        onClick = {
          Navigation.findNavController(requireView()).navigate(R.id.action_accountSettingsFragment_to_oldDeviceTransferActivity)
        }
      )*/

      clickPref(
        title = DSLSettingsText.from(R.string.preferences__delete_account, ContextCompat.getColor(requireContext(), R.color.signal_alert_primary)),
        onClick = {
          Navigation.findNavController(requireView()).navigate(R.id.action_accountSettingsFragment_to_deleteAccountFragment)
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
    if (!enabled) {
      val context: Context = requireContext()
      val mp03CustomDialog = Mp03CustomDialog(getContext())
      mp03CustomDialog.setTitle(context.resources.getString(R.string.preferences_app_protection__confirm_your_signal_pin))
      mp03CustomDialog.setMessage(context.resources.getString(R.string.preferences_app_protection__make_sure_you_memorize_or_securely_store_your_pin))
      mp03CustomDialog.setPositiveListener(R.string.preferences_app_protection__turn_off) {
        val pinEditText = mp03CustomDialog.findViewById<EditText>(R.id.reminder_disable_pin_0)
        val pin = pinEditText.text.toString()
        if (pin.length < KbsConstants.MINIMUM_PIN_LENGTH) {
          Toast.makeText(context, context.resources.getString(R.string.preferences_app_protection__incorrect_pin_try_again), Toast.LENGTH_SHORT).show()
        }
        val correct = PinHashing.verifyLocalPinHash(SignalStore.kbsValues().localPinHash!!, pin)
        if (correct) {
          SignalStore.pinValues().setPinRemindersEnabled(false)
          viewModel.refreshState()
          mp03CustomDialog.dismiss()
          1
        } else {
          Toast.makeText(context, requireActivity().resources.getString(R.string.preferences_app_protection__incorrect_pin_try_again), Toast.LENGTH_SHORT).show()
          0
        }
      }
      mp03CustomDialog.setNegativeListener(android.R.string.cancel) { 1 }
      mp03CustomDialog.setBackKeyListener { requireActivity().finish()}
      mp03CustomDialog.show()
      val pinEditText = mp03CustomDialog.findViewById<EditText>(R.id.reminder_disable_pin_0)
      if (pinEditText.requestFocus()) {
        ServiceUtil.getInputMethodManager(pinEditText.context).showSoftInput(pinEditText, 0)
      }
      when (SignalStore.pinValues().keyboardType) {
        PinKeyboardType.NUMERIC -> pinEditText.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        PinKeyboardType.ALPHA_NUMERIC -> pinEditText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        else -> throw AssertionError("Unexpected type!")
      }
      pinEditText.addTextChangedListener(object : SimpleTextWatcher() {
        override fun onTextChanged(text: String) {
          //mp03CustomDialog.findViewById(R.id.reminder_disable_turn_off).setEnabled(text.length() >= KbsConstants.MINIMUM_PIN_LENGTH);
        }
      })
    } else {
      SignalStore.pinValues().setPinRemindersEnabled(true)
      viewModel.refreshState()
    }
  }
}
