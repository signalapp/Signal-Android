package org.thoughtcrime.securesms.components.settings.app.account

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Typeface
import android.text.InputType
import android.util.DisplayMetrics
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.autofill.HintConstants
import androidx.core.app.DialogCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.Navigation
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.contactshare.SimpleTextWatcher
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.lock.v2.CreateSvrPinActivity
import org.thoughtcrime.securesms.lock.v2.PinKeyboardType
import org.thoughtcrime.securesms.lock.v2.SvrConstants
import org.thoughtcrime.securesms.pin.RegistrationLockV2Dialog
import org.thoughtcrime.securesms.registration.ui.RegistrationActivity
import org.thoughtcrime.securesms.util.PlayStoreUtil
import org.thoughtcrime.securesms.util.ServiceUtil
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.whispersystems.signalservice.api.kbs.PinHashUtil

class AccountSettingsFragment : DSLSettingsFragment(R.string.AccountSettingsFragment__account) {

  lateinit var viewModel: AccountSettingsViewModel

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    if (requestCode == CreateSvrPinActivity.REQUEST_NEW_PIN && resultCode == CreateSvrPinActivity.RESULT_OK) {
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
        isEnabled = state.isDeprecatedOrUnregistered(),
        onClick = {
          if (state.hasPin) {
            startActivityForResult(CreateSvrPinActivity.getIntentForPinChangeFromSettings(requireContext()), CreateSvrPinActivity.REQUEST_NEW_PIN)
          } else {
            startActivityForResult(CreateSvrPinActivity.getIntentForPinCreate(requireContext()), CreateSvrPinActivity.REQUEST_NEW_PIN)
          }
        }
      )

      switchPref(
        title = DSLSettingsText.from(R.string.preferences_app_protection__pin_reminders),
        summary = DSLSettingsText.from(R.string.AccountSettingsFragment__youll_be_asked_less_frequently),
        isChecked = state.hasPin && state.pinRemindersEnabled,
        isEnabled = state.hasPin && state.isDeprecatedOrUnregistered(),
        onClick = {
          setPinRemindersEnabled(!state.pinRemindersEnabled)
        }
      )

      switchPref(
        title = DSLSettingsText.from(R.string.preferences_app_protection__registration_lock),
        summary = DSLSettingsText.from(R.string.AccountSettingsFragment__require_your_signal_pin),
        isChecked = state.registrationLockEnabled,
        isEnabled = state.hasPin && state.isDeprecatedOrUnregistered(),
        onClick = {
          setRegistrationLockEnabled(!state.registrationLockEnabled)
        }
      )

      clickPref(
        title = DSLSettingsText.from(R.string.preferences__advanced_pin_settings),
        isEnabled = state.isDeprecatedOrUnregistered(),
        onClick = {
          Navigation.findNavController(requireView()).safeNavigate(R.id.action_accountSettingsFragment_to_advancedPinSettingsActivity)
        }
      )

      dividerPref()

      sectionHeaderPref(R.string.AccountSettingsFragment__account)

      if (SignalStore.account.isRegistered) {
        clickPref(
          title = DSLSettingsText.from(R.string.AccountSettingsFragment__change_phone_number),
          isEnabled = state.isDeprecatedOrUnregistered(),
          onClick = {
            Navigation.findNavController(requireView()).safeNavigate(R.id.action_accountSettingsFragment_to_changePhoneNumberFragment)
          }
        )
      }

      clickPref(
        title = DSLSettingsText.from(R.string.preferences_chats__transfer_account),
        summary = DSLSettingsText.from(R.string.preferences_chats__transfer_account_to_a_new_android_device),
        isEnabled = state.isDeprecatedOrUnregistered(),
        onClick = {
          Navigation.findNavController(requireView()).safeNavigate(R.id.action_accountSettingsFragment_to_oldDeviceTransferActivity)
        }
      )

      clickPref(
        title = DSLSettingsText.from(R.string.AccountSettingsFragment__request_account_data),
        isEnabled = state.isDeprecatedOrUnregistered(),
        onClick = {
          Navigation.findNavController(requireView()).safeNavigate(R.id.action_accountSettingsFragment_to_exportAccountFragment)
        }
      )

      if (!state.isDeprecatedOrUnregistered()) {
        if (state.clientDeprecated) {
          clickPref(
            title = DSLSettingsText.from(R.string.preferences_account_update_signal),
            onClick = {
              PlayStoreUtil.openPlayStoreOrOurApkDownloadPage(requireContext())
            }
          )
        } else if (state.userUnregistered) {
          clickPref(
            title = DSLSettingsText.from(R.string.preferences_account_reregister),
            onClick = {
              startActivity(RegistrationActivity.newIntentForReRegistration(requireContext()))
            }
          )
        }

        clickPref(
          title = DSLSettingsText.from(R.string.preferences_account_delete_all_data, ContextCompat.getColor(requireContext(), R.color.signal_alert_primary)),
          onClick = {
            MaterialAlertDialogBuilder(requireContext())
              .setTitle(R.string.preferences_account_delete_all_data_confirmation_title)
              .setMessage(R.string.preferences_account_delete_all_data_confirmation_message)
              .setPositiveButton(R.string.preferences_account_delete_all_data_confirmation_proceed) { _: DialogInterface, _: Int ->
                if (!ServiceUtil.getActivityManager(AppDependencies.application).clearApplicationUserData()) {
                  Toast.makeText(requireContext(), R.string.preferences_account_delete_all_data_failed, Toast.LENGTH_LONG).show()
                }
              }
              .setNegativeButton(R.string.preferences_account_delete_all_data_confirmation_cancel, null)
              .show()
          }
        )
      }

      clickPref(
        title = DSLSettingsText.from(R.string.preferences__delete_account, ContextCompat.getColor(requireContext(), if (state.isDeprecatedOrUnregistered()) R.color.signal_alert_primary else R.color.signal_alert_primary_50)),
        isEnabled = state.isDeprecatedOrUnregistered(),
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
    if (!enabled) {
      val context: Context = requireContext()
      val metrics: DisplayMetrics = resources.displayMetrics

      val dialog: AlertDialog = MaterialAlertDialogBuilder(context)
        .setView(R.layout.pin_disable_reminders_dialog)
        .setOnDismissListener { viewModel.refreshState() }
        .create()

      dialog.show()
      dialog.window!!.setLayout((metrics.widthPixels * .80).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)

      val pinEditText = DialogCompat.requireViewById(dialog, R.id.reminder_disable_pin) as EditText
      val statusText = DialogCompat.requireViewById(dialog, R.id.reminder_disable_status) as TextView
      val cancelButton = DialogCompat.requireViewById(dialog, R.id.reminder_disable_cancel)
      val turnOffButton = DialogCompat.requireViewById(dialog, R.id.reminder_disable_turn_off)
      val changeKeyboard = DialogCompat.requireViewById(dialog, R.id.reminder_change_keyboard) as MaterialButton

      changeKeyboard.setOnClickListener {
        if (pinEditText.inputType and InputType.TYPE_CLASS_NUMBER == 0) {
          pinEditText.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
          changeKeyboard.setIconResource(PinKeyboardType.ALPHA_NUMERIC.iconResource)
        } else {
          pinEditText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
          changeKeyboard.setIconResource(PinKeyboardType.NUMERIC.iconResource)
        }
        pinEditText.typeface = Typeface.DEFAULT
      }

      pinEditText.post {
        ViewUtil.focusAndShowKeyboard(pinEditText)
      }

      ViewCompat.setAutofillHints(pinEditText, HintConstants.AUTOFILL_HINT_PASSWORD)

      when (SignalStore.pin.keyboardType) {
        PinKeyboardType.NUMERIC -> {
          pinEditText.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
          changeKeyboard.setIconResource(PinKeyboardType.ALPHA_NUMERIC.iconResource)
        }
        PinKeyboardType.ALPHA_NUMERIC -> {
          pinEditText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
          changeKeyboard.setIconResource(PinKeyboardType.NUMERIC.iconResource)
        }
      }

      pinEditText.addTextChangedListener(object : SimpleTextWatcher() {
        override fun onTextChanged(text: String) {
          turnOffButton.isEnabled = text.length >= SvrConstants.MINIMUM_PIN_LENGTH
        }
      })

      pinEditText.typeface = Typeface.DEFAULT
      turnOffButton.setOnClickListener {
        val pin = pinEditText.text.toString()
        val correct = PinHashUtil.verifyLocalPinHash(SignalStore.svr.localPinHash!!, pin)
        if (correct) {
          SignalStore.pin.setPinRemindersEnabled(false)
          viewModel.refreshState()
          dialog.dismiss()
        } else {
          statusText.setText(R.string.preferences_app_protection__incorrect_pin_try_again)
        }
      }

      cancelButton.setOnClickListener { dialog.dismiss() }
    } else {
      SignalStore.pin.setPinRemindersEnabled(true)
      viewModel.refreshState()
    }
  }
}
