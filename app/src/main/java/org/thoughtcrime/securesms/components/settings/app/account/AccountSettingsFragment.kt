package org.thoughtcrime.securesms.components.settings.app.account

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.util.DisplayMetrics
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.core.app.DialogCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalPreview
import org.signal.core.ui.compose.Texts
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.compose.rememberStatusBarColorNestedScrollModifier
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
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.whispersystems.signalservice.api.kbs.PinHashUtil

class AccountSettingsFragment : ComposeFragment() {

  private val viewModel: AccountSettingsViewModel by viewModels()

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    if (requestCode == CreateSvrPinActivity.REQUEST_NEW_PIN && resultCode == CreateSvrPinActivity.RESULT_OK) {
      Snackbar.make(requireView(), R.string.ConfirmKbsPinFragment__pin_created, Snackbar.LENGTH_LONG).show()
    }
  }

  override fun onResume() {
    super.onResume()
    viewModel.refreshState()
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val callbacks = remember { Callbacks() }

    AccountSettingsScreen(
      state = state,
      callbacks = callbacks
    )
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
        val newType = PinKeyboardType.fromEditText(pinEditText).other
        newType.applyTo(
          pinEditText = pinEditText,
          toggleTypeButton = changeKeyboard
        )
        pinEditText.typeface = Typeface.DEFAULT
      }

      pinEditText.post {
        ViewUtil.focusAndShowKeyboard(pinEditText)
      }

      SignalStore.pin.keyboardType.applyTo(
        pinEditText = pinEditText,
        toggleTypeButton = changeKeyboard
      )

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

  private inner class Callbacks : AccountSettingsScreenCallbacks {
    override fun onNavigationClick() {
      activity?.onBackPressedDispatcher?.onBackPressed()
    }

    @Suppress("DEPRECATION")
    override fun onChangePinClick() {
      startActivityForResult(CreateSvrPinActivity.getIntentForPinChangeFromSettings(requireContext()), CreateSvrPinActivity.REQUEST_NEW_PIN)
    }

    @Suppress("DEPRECATION")
    override fun onCreatePinClick() {
      startActivityForResult(CreateSvrPinActivity.getIntentForPinCreate(requireContext()), CreateSvrPinActivity.REQUEST_NEW_PIN)
    }

    override fun setPinRemindersEnabled(enabled: Boolean) {
      this@AccountSettingsFragment.setPinRemindersEnabled(enabled)
    }

    override fun setRegistrationLockEnabled(enabled: Boolean) {
      this@AccountSettingsFragment.setRegistrationLockEnabled(enabled)
    }

    override fun openAdvancedPinSettings() {
      findNavController().safeNavigate(R.id.action_accountSettingsFragment_to_advancedPinSettingsActivity)
    }

    override fun openChangeNumberFlow() {
      findNavController().safeNavigate(R.id.action_accountSettingsFragment_to_changePhoneNumberFragment)
    }

    override fun openDeviceTransferFlow() {
      findNavController().safeNavigate(R.id.action_accountSettingsFragment_to_oldDeviceTransferActivity)
    }

    override fun openExportAccountDataFlow() {
      findNavController().safeNavigate(R.id.action_accountSettingsFragment_to_exportAccountFragment)
    }

    override fun openUpdateAppFlow() {
      PlayStoreUtil.openPlayStoreOrOurApkDownloadPage(requireContext())
    }

    override fun openReRegistrationFlow() {
      startActivity(RegistrationActivity.newIntentForReRegistration(requireContext()))
    }

    override fun openDeleteAccountFlow() {
      findNavController().safeNavigate(R.id.action_accountSettingsFragment_to_deleteAccountFragment)
    }

    override fun deleteAllData() {
      if (!ServiceUtil.getActivityManager(AppDependencies.application).clearApplicationUserData()) {
        Toast.makeText(requireContext(), R.string.preferences_account_delete_all_data_failed, Toast.LENGTH_LONG).show()
      }
    }
  }
}

@Stable
@VisibleForTesting
interface AccountSettingsScreenCallbacks {

  fun onNavigationClick() = Unit
  fun onChangePinClick() = Unit
  fun onCreatePinClick() = Unit
  fun setPinRemindersEnabled(enabled: Boolean) = Unit
  fun setRegistrationLockEnabled(enabled: Boolean) = Unit
  fun openAdvancedPinSettings() = Unit
  fun openChangeNumberFlow() = Unit
  fun openDeviceTransferFlow() = Unit
  fun openExportAccountDataFlow() = Unit
  fun openUpdateAppFlow() = Unit
  fun openReRegistrationFlow() = Unit
  fun openDeleteAccountFlow() = Unit
  fun deleteAllData() = Unit

  object Empty : AccountSettingsScreenCallbacks
}

@VisibleForTesting
object AccountSettingsTestTags {
  const val SCROLLER = "scroller"
  const val ROW_MODIFY_PIN = "row-modify-pin"
  const val ROW_PIN_REMINDER = "row-pin-reminder"
  const val ROW_REGISTRATION_LOCK = "row-registration-lock"
  const val ROW_ADVANCED_PIN_SETTINGS = "row-advanced-pin-settings"
  const val ROW_CHANGE_PHONE_NUMBER = "row-change-phone-number"
  const val ROW_TRANSFER_ACCOUNT = "row-transfer-account"
  const val ROW_REQUEST_ACCOUNT_DATA = "row-request-account-data"
  const val ROW_UPDATE_SIGNAL = "row-update-signal"
  const val ROW_RE_REGISTER = "row-re-register"
  const val ROW_DELETE_ALL_DATA = "row-delete-all-data"
  const val ROW_DELETE_ACCOUNT = "row-delete-account"
  const val DIALOG_CONFIRM_DELETE_ALL_DATA = "dialog-confirm-delete-all-data"
}

@Composable
@VisibleForTesting
fun AccountSettingsScreen(
  state: AccountSettingsState,
  callbacks: AccountSettingsScreenCallbacks
) {
  Scaffolds.Settings(
    title = stringResource(R.string.AccountSettingsFragment__account),
    onNavigationClick = callbacks::onNavigationClick,
    navigationIcon = ImageVector.vectorResource(R.drawable.ic_arrow_left_24)
  ) { contentPadding ->
    LazyColumn(
      modifier = Modifier
        .padding(contentPadding)
        .then(rememberStatusBarColorNestedScrollModifier())
        .testTag(AccountSettingsTestTags.SCROLLER)
    ) {
      item {
        Texts.SectionHeader(
          text = stringResource(R.string.preferences_app_protection__signal_pin)
        )
      }

      item {
        @StringRes val textId = if (state.hasPin || state.hasRestoredAep) {
          R.string.preferences_app_protection__change_your_pin
        } else {
          R.string.preferences_app_protection__create_a_pin
        }

        Rows.TextRow(
          text = stringResource(textId),
          enabled = state.isNotDeprecatedOrUnregistered(),
          onClick = {
            if (state.hasPin) {
              callbacks.onChangePinClick()
            } else {
              callbacks.onCreatePinClick()
            }
          },
          modifier = Modifier.testTag(AccountSettingsTestTags.ROW_MODIFY_PIN)
        )
      }

      item {
        Rows.ToggleRow(
          text = stringResource(R.string.preferences_app_protection__pin_reminders),
          label = stringResource(R.string.AccountSettingsFragment__youll_be_asked_less_frequently),
          checked = state.hasPin && state.pinRemindersEnabled,
          enabled = state.hasPin && state.isNotDeprecatedOrUnregistered(),
          onCheckChanged = callbacks::setPinRemindersEnabled,
          modifier = Modifier.testTag(AccountSettingsTestTags.ROW_PIN_REMINDER)
        )
      }

      item {
        Rows.ToggleRow(
          text = stringResource(R.string.preferences_app_protection__registration_lock),
          label = stringResource(R.string.AccountSettingsFragment__require_your_signal_pin),
          checked = state.registrationLockEnabled,
          enabled = state.hasPin && state.isNotDeprecatedOrUnregistered(),
          onCheckChanged = callbacks::setRegistrationLockEnabled,
          modifier = Modifier.testTag(AccountSettingsTestTags.ROW_REGISTRATION_LOCK)
        )
      }

      item {
        Rows.TextRow(
          text = stringResource(R.string.preferences__advanced_pin_settings),
          enabled = state.isNotDeprecatedOrUnregistered(),
          onClick = callbacks::openAdvancedPinSettings,
          modifier = Modifier.testTag(AccountSettingsTestTags.ROW_ADVANCED_PIN_SETTINGS)
        )
      }

      item {
        Dividers.Default()
      }

      item {
        Texts.SectionHeader(
          text = stringResource(R.string.AccountSettingsFragment__account)
        )
      }

      if (!state.userUnregistered) {
        item {
          Rows.TextRow(
            text = stringResource(R.string.AccountSettingsFragment__change_phone_number),
            enabled = state.isNotDeprecatedOrUnregistered(),
            onClick = callbacks::openChangeNumberFlow,
            modifier = Modifier.testTag(AccountSettingsTestTags.ROW_CHANGE_PHONE_NUMBER)
          )
        }
      }

      item {
        Rows.TextRow(
          text = stringResource(R.string.preferences_chats__transfer_account),
          label = stringResource(R.string.preferences_chats__transfer_account_to_a_new_android_device),
          enabled = state.canTransferWhileUnregistered || state.isNotDeprecatedOrUnregistered(),
          onClick = callbacks::openDeviceTransferFlow,
          modifier = Modifier.testTag(AccountSettingsTestTags.ROW_TRANSFER_ACCOUNT)
        )
      }

      item {
        Rows.TextRow(
          text = stringResource(R.string.AccountSettingsFragment__request_account_data),
          enabled = state.isNotDeprecatedOrUnregistered(),
          onClick = callbacks::openExportAccountDataFlow,
          modifier = Modifier.testTag(AccountSettingsTestTags.ROW_REQUEST_ACCOUNT_DATA)
        )
      }

      if (!state.isNotDeprecatedOrUnregistered()) {
        if (state.clientDeprecated) {
          item {
            Rows.TextRow(
              text = stringResource(R.string.preferences_account_update_signal),
              onClick = callbacks::openUpdateAppFlow,
              modifier = Modifier.testTag(AccountSettingsTestTags.ROW_UPDATE_SIGNAL)
            )
          }
        } else if (state.userUnregistered) {
          item {
            Rows.TextRow(
              text = stringResource(R.string.preferences_account_reregister),
              onClick = callbacks::openReRegistrationFlow,
              modifier = Modifier.testTag(AccountSettingsTestTags.ROW_RE_REGISTER)
            )
          }
        }

        item {
          var displayDialog by remember { mutableStateOf(false) }

          Rows.TextRow(
            text = {
              Text(
                text = stringResource(R.string.preferences_account_delete_all_data),
                style = MaterialTheme.typography.bodyLarge,
                color = colorResource(R.color.signal_alert_primary)
              )
            },
            onClick = {
              displayDialog = true
            },
            modifier = Modifier.testTag(AccountSettingsTestTags.ROW_DELETE_ALL_DATA)
          )

          if (displayDialog) {
            DeleteAllDataConfirmationDialog(
              onDismissRequest = { displayDialog = false },
              onConfirm = callbacks::deleteAllData
            )
          }
        }
      }

      item {
        @ColorRes val textColor = if (state.isNotDeprecatedOrUnregistered()) {
          R.color.signal_alert_primary
        } else {
          R.color.signal_alert_primary_50
        }

        Rows.TextRow(
          text = {
            Text(
              text = stringResource(R.string.preferences__delete_account),
              color = colorResource(textColor)
            )
          },
          enabled = state.isNotDeprecatedOrUnregistered(),
          onClick = callbacks::openDeleteAccountFlow,
          modifier = Modifier.testTag(AccountSettingsTestTags.ROW_DELETE_ACCOUNT)
        )
      }
    }
  }
}

@Composable
private fun DeleteAllDataConfirmationDialog(
  onConfirm: () -> Unit,
  onDismissRequest: () -> Unit
) {
  Dialogs.SimpleAlertDialog(
    title = stringResource(R.string.preferences_account_delete_all_data_confirmation_title),
    body = stringResource(R.string.preferences_account_delete_all_data_confirmation_message),
    confirm = stringResource(R.string.preferences_account_delete_all_data_confirmation_proceed),
    onConfirm = onConfirm,
    dismiss = stringResource(R.string.preferences_account_delete_all_data_confirmation_cancel),
    onDismissRequest = onDismissRequest,
    modifier = Modifier.testTag(AccountSettingsTestTags.DIALOG_CONFIRM_DELETE_ALL_DATA)
  )
}

@SignalPreview
@Composable
private fun AccountSettingsScreenPreview() {
  Previews.Preview {
    AccountSettingsScreen(
      state = AccountSettingsState(
        hasPin = true,
        hasRestoredAep = true,
        pinRemindersEnabled = true,
        registrationLockEnabled = true,
        userUnregistered = false,
        clientDeprecated = false,
        canTransferWhileUnregistered = true
      ),
      callbacks = AccountSettingsScreenCallbacks.Empty
    )
  }
}

@SignalPreview
@Composable
private fun DeleteAllDataConfirmationDialogPreview() {
  Previews.Preview {
    DeleteAllDataConfirmationDialog({}, {})
  }
}
