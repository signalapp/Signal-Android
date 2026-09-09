/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.account

import android.text.format.DateUtils
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.signal.appsettings.R
import org.signal.appsettings.account.AccountSettingsState.Dialog
import org.signal.appsettings.account.AccountSettingsState.LoadState
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.DropdownMenus
import org.signal.core.ui.compose.PinVisualTransformation
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Rows.TextAndLabel
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.Texts
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.signallogin.beta.SignalLoginBetaDisclaimer
import org.signal.signallogin.beta.SignalLoginBetaTag
import org.signal.core.ui.R as CoreUiR

@VisibleForTesting
object AccountSettingsTestTags {
  const val SCROLLER = "scroller"
  const val CARD_SIGNAL_LOGIN = "card-signal-login"
  const val ROW_SET_UP_TWO_FACTOR = "row-set-up-two-factor"
  const val MENU_ITEM_AUTHENTICATOR_APP = "menu-item-authenticator-app"
  const val ROW_TWO_FACTOR_METHOD = "row-two-factor-method"
  const val BUTTON_METHOD_MENU = "button-method-menu"
  const val MENU_ITEM_RENAME = "menu-item-rename"
  const val MENU_ITEM_REMOVE = "menu-item-remove"
  const val TWO_FACTOR_LOADING = "two-factor-loading"
  const val TWO_FACTOR_LOAD_FAILED_MESSAGE = "two-factor-load-failed-message"
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
  const val DIALOG_CONFIRM_PIN = "dialog-confirm-pin"
  const val DIALOG_CONFIRM_REGISTRATION_LOCK = "dialog-confirm-registration-lock"
  const val DIALOG_CONFIRM_REMOVE_TOTP_APP = "dialog-confirm-remove-totp-app"
  const val DIALOG_MAX_TOTP_APPS_REACHED = "dialog-max-totp-apps-reached"
  const val PIN_INPUT = "pin-input"
  const val PIN_KEYBOARD_TOGGLE = "pin-keyboard-toggle"
}

@Composable
fun AccountSettingsScreen(
  state: AccountSettingsState,
  onEvent: (AccountSettingsEvent) -> Unit
) {
  Scaffolds.Settings(
    title = stringResource(R.string.AccountSettingsFragment__account),
    onNavigationClick = { onEvent(AccountSettingsEvent.NavigateBackClicked) },
    navigationIcon = SignalIcons.ArrowStart.imageVector
  ) { contentPadding ->
    LazyColumn(
      modifier = Modifier
        .padding(contentPadding)
        .testTag(AccountSettingsTestTags.SCROLLER)
    ) {
      if (state.signalLogin != null) {
        item {
          Texts.SectionHeader(
            text = stringResource(R.string.AccountSettingsFragment__signal_login),
            trailingContent = { SignalLoginBetaTag() }
          )
        }

        item {
          SignalLoginBetaDisclaimer(
            modifier = Modifier
              .padding(horizontal = dimensionResource(CoreUiR.dimen.gutter))
              .padding(bottom = 12.dp)
          )
        }

        item {
          SignalLoginCard(
            onClick = { onEvent(AccountSettingsEvent.AccountAndRecoveryClicked) }
          )
        }

        item {
          SectionFooter(text = stringResource(R.string.AccountSettingsFragment__your_signal_login_is_used_to_recover))
        }

        item {
          Dividers.Default()
        }

        item {
          Texts.SectionHeader(
            text = stringResource(R.string.AccountSettingsFragment__two_factor_authentication)
          )
        }

        item {
          SetUpTwoFactorRow(onEvent = onEvent)
        }

        when (state.signalLogin.loadState) {
          LoadState.LOADING -> item {
            Box(
              contentAlignment = Alignment.Center,
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
            ) {
              CircularProgressIndicator(
                modifier = Modifier
                  .size(24.dp)
                  .testTag(AccountSettingsTestTags.TWO_FACTOR_LOADING)
              )
            }
          }

          LoadState.NETWORK_FAILURE -> item {
            Text(
              text = stringResource(R.string.AccountSettingsFragment__couldnt_load_your_two_factor_methods),
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier
                .padding(horizontal = dimensionResource(CoreUiR.dimen.gutter), vertical = 16.dp)
                .testTag(AccountSettingsTestTags.TWO_FACTOR_LOAD_FAILED_MESSAGE)
            )
          }

          LoadState.LOADED -> items(state.signalLogin.twoFactorMethods, key = { "${it.kind}:${it.id}" }) { method ->
            TwoFactorMethodRow(
              method = method,
              onEvent = onEvent
            )
          }
        }

        item {
          SectionFooter(text = stringResource(R.string.AccountSettingsFragment__use_a_second_form_of_authentication))
        }

        item {
          Dividers.Default()
        }
      }

      if (!state.isPhoneNumberless) {
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
            enabled = state.isNotDeprecatedOrUnregistered,
            onClick = { onEvent(AccountSettingsEvent.ModifyPinClicked) },
            modifier = Modifier.testTag(AccountSettingsTestTags.ROW_MODIFY_PIN)
          )
        }

        item {
          Rows.ToggleRow(
            text = stringResource(R.string.preferences_app_protection__pin_reminders),
            label = stringResource(R.string.AccountSettingsFragment__youll_be_asked_less_frequently),
            checked = state.hasPin && state.pinRemindersEnabled,
            enabled = state.hasPin && state.isNotDeprecatedOrUnregistered,
            onCheckChanged = { onEvent(AccountSettingsEvent.PinRemindersToggled(it)) },
            modifier = Modifier.testTag(AccountSettingsTestTags.ROW_PIN_REMINDER)
          )
        }

        item {
          Rows.ToggleRow(
            text = stringResource(R.string.preferences_app_protection__registration_lock),
            label = stringResource(R.string.AccountSettingsFragment__require_your_signal_pin),
            checked = state.registrationLockEnabled,
            enabled = state.hasPin && state.isNotDeprecatedOrUnregistered,
            onCheckChanged = { onEvent(AccountSettingsEvent.RegistrationLockToggled(it)) },
            modifier = Modifier.testTag(AccountSettingsTestTags.ROW_REGISTRATION_LOCK)
          )
        }

        item {
          Rows.TextRow(
            text = stringResource(R.string.preferences__advanced_pin_settings),
            enabled = state.isNotDeprecatedOrUnregistered,
            onClick = { onEvent(AccountSettingsEvent.AdvancedPinSettingsClicked) },
            modifier = Modifier.testTag(AccountSettingsTestTags.ROW_ADVANCED_PIN_SETTINGS)
          )
        }

        item {
          Dividers.Default()
        }
      }

      item {
        Texts.SectionHeader(
          text = stringResource(R.string.AccountSettingsFragment__account)
        )
      }

      if (!state.userUnregistered && !state.isPhoneNumberless) {
        item {
          Rows.TextRow(
            text = stringResource(R.string.AccountSettingsFragment__change_phone_number),
            enabled = state.isNotDeprecatedOrUnregistered,
            onClick = { onEvent(AccountSettingsEvent.ChangePhoneNumberClicked) },
            modifier = Modifier.testTag(AccountSettingsTestTags.ROW_CHANGE_PHONE_NUMBER)
          )
        }
      }

      item {
        Rows.TextRow(
          text = stringResource(R.string.preferences_chats__transfer_account),
          label = stringResource(R.string.preferences_chats__transfer_account_to_a_new_android_device),
          enabled = state.canTransferWhileUnregistered || state.isNotDeprecatedOrUnregistered,
          onClick = { onEvent(AccountSettingsEvent.TransferAccountClicked) },
          modifier = Modifier.testTag(AccountSettingsTestTags.ROW_TRANSFER_ACCOUNT)
        )
      }

      item {
        Rows.TextRow(
          text = stringResource(R.string.AccountSettingsFragment__request_account_data),
          enabled = state.isNotDeprecatedOrUnregistered,
          onClick = { onEvent(AccountSettingsEvent.RequestAccountDataClicked) },
          modifier = Modifier.testTag(AccountSettingsTestTags.ROW_REQUEST_ACCOUNT_DATA)
        )
      }

      if (!state.isNotDeprecatedOrUnregistered) {
        if (state.clientDeprecated) {
          item {
            Rows.TextRow(
              text = stringResource(R.string.preferences_account_update_signal),
              onClick = { onEvent(AccountSettingsEvent.UpdateSignalClicked) },
              modifier = Modifier.testTag(AccountSettingsTestTags.ROW_UPDATE_SIGNAL)
            )
          }
        } else if (state.userUnregistered) {
          item {
            Rows.TextRow(
              text = stringResource(R.string.preferences_account_reregister),
              onClick = { onEvent(AccountSettingsEvent.ReRegisterClicked) },
              modifier = Modifier.testTag(AccountSettingsTestTags.ROW_RE_REGISTER)
            )
          }
        }

        item {
          Rows.TextRow(
            text = {
              Text(
                text = stringResource(R.string.preferences_account_delete_all_data),
                style = MaterialTheme.typography.bodyLarge,
                color = SignalTheme.colors.colorAlert
              )
            },
            onClick = { onEvent(AccountSettingsEvent.DeleteAllDataClicked) },
            modifier = Modifier.testTag(AccountSettingsTestTags.ROW_DELETE_ALL_DATA)
          )
        }
      }

      item {
        val textColor = if (state.isNotDeprecatedOrUnregistered) {
          SignalTheme.colors.colorAlert
        } else {
          SignalTheme.colors.colorAlertDisabled
        }

        Rows.TextRow(
          text = {
            Text(
              text = stringResource(R.string.preferences__delete_account),
              color = textColor
            )
          },
          enabled = state.isNotDeprecatedOrUnregistered,
          onClick = { onEvent(AccountSettingsEvent.DeleteAccountClicked) },
          modifier = Modifier.testTag(AccountSettingsTestTags.ROW_DELETE_ACCOUNT)
        )
      }
    }
  }

  when (val dialog = state.dialog) {
    Dialog.None -> Unit
    Dialog.ConfirmDeleteAllData -> DeleteAllDataConfirmationDialog(onEvent)
    is Dialog.ConfirmPinToDisableReminders -> ConfirmPinToDisableRemindersDialog(dialog, onEvent)
    is Dialog.ConfirmRegistrationLock -> {
      if (dialog.inProgress) {
        Dialogs.IndeterminateProgressDialog()
      } else {
        RegistrationLockConfirmationDialog(dialog, onEvent)
      }
    }
    is Dialog.ConfirmRemoveTotpApp -> ConfirmRemoveTotpAppDialog(onEvent = onEvent)
    Dialog.MaxTotpAppsReached -> MaxTotpAppsReachedDialog(maxApps = state.signalLogin?.maxTotpApps ?: 0, onEvent = onEvent)
  }
}

/**
 * The row that starts adding a second factor, which offers a choice of what to add. Passkeys aren't supported yet, so
 * an authenticator app is the only thing there is to choose.
 */
@Composable
private fun SetUpTwoFactorRow(
  onEvent: (AccountSettingsEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  val menuController = remember { DropdownMenus.MenuController() }

  Box(modifier = modifier) {
    Rows.TextRow(
      icon = {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
          Icon(
            imageVector = SignalIcons.Plus.imageVector,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface
          )
        }
      },
      text = {
        TextAndLabel(text = stringResource(R.string.AccountSettingsFragment__set_up))
      },
      onClick = menuController::show,
      modifier = Modifier.testTag(AccountSettingsTestTags.ROW_SET_UP_TWO_FACTOR)
    )

    DropdownMenus.Menu(controller = menuController) { controller ->
      DropdownMenus.Item(
        leadingIconResId = CoreUiR.drawable.symbol_device_phone_24,
        text = {
          Column {
            Text(text = stringResource(R.string.AccountSettingsFragment__authenticator_app))

            Text(
              text = stringResource(R.string.AccountSettingsFragment__one_time_verification_codes),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        },
        onClick = {
          onEvent(AccountSettingsEvent.AddTotpAppClicked)
          controller.hide()
        },
        modifier = Modifier.testTag(AccountSettingsTestTags.MENU_ITEM_AUTHENTICATOR_APP)
      )
    }
  }
}

@Composable
private fun TwoFactorMethodRow(
  method: TwoFactorMethod,
  onEvent: (AccountSettingsEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val addedTime = remember(method.createdAt) {
    DateUtils.getRelativeDateTimeString(context, method.createdAt, DateUtils.DAY_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0).toString()
  }

  val icon = when (method.kind) {
    TwoFactorMethod.Kind.AUTHENTICATOR_APP -> SignalIcons.DevicePhone
    TwoFactorMethod.Kind.PASSKEY -> SignalIcons.Key
  }

  val kindName = when (method.kind) {
    TwoFactorMethod.Kind.AUTHENTICATOR_APP -> stringResource(R.string.AccountSettingsFragment__authenticator_app)
    TwoFactorMethod.Kind.PASSKEY -> stringResource(R.string.AccountSettingsFragment__passkey)
  }

  Rows.TextRow(
    icon = {
      Icon(
        painter = icon.painter,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurface
      )
    },
    text = {
      TextAndLabel(
        text = method.name,
        label = stringResource(R.string.AccountSettingsFragment__s_added_s, kindName, addedTime)
      )

      TwoFactorMethodMenuButton(
        method = method,
        onEvent = onEvent
      )
    },
    modifier = modifier.testTag(AccountSettingsTestTags.ROW_TWO_FACTOR_METHOD)
  )
}

@Composable
private fun TwoFactorMethodMenuButton(
  method: TwoFactorMethod,
  onEvent: (AccountSettingsEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  val menuController = remember { DropdownMenus.MenuController() }

  Box(modifier = modifier) {
    IconButton(
      onClick = menuController::show,
      modifier = Modifier.testTag(AccountSettingsTestTags.BUTTON_METHOD_MENU)
    ) {
      Icon(
        imageVector = SignalIcons.MoreVertical.imageVector,
        contentDescription = stringResource(R.string.AccountSettingsFragment__open_two_factor_options),
        tint = MaterialTheme.colorScheme.onSurface
      )
    }

    DropdownMenus.Menu(controller = menuController) { controller ->
      DropdownMenus.Item(
        leadingIconResId = CoreUiR.drawable.symbol_edit_24,
        text = { Text(text = stringResource(R.string.AccountSettingsFragment__rename)) },
        onClick = {
          onEvent(AccountSettingsEvent.RenameMethodClicked(method))
          controller.hide()
        },
        modifier = Modifier.testTag(AccountSettingsTestTags.MENU_ITEM_RENAME)
      )

      DropdownMenus.Item(
        leadingIconResId = CoreUiR.drawable.symbol_x_circle_24,
        text = { Text(text = stringResource(R.string.AccountSettingsFragment__remove)) },
        onClick = {
          onEvent(AccountSettingsEvent.RemoveMethodClicked(method))
          controller.hide()
        },
        modifier = Modifier.testTag(AccountSettingsTestTags.MENU_ITEM_REMOVE)
      )
    }
  }
}

/**
 * The card at the top of the screen that summarizes the user's Signal Login, which opens the screen showing the full
 * keys.
 */
@Composable
private fun SignalLoginCard(
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 20.dp)
      .clip(RoundedCornerShape(18.dp))
      .background(SignalTheme.colors.colorSurface2)
      .clickable(onClick = onClick)
      .padding(horizontal = 18.dp, vertical = 20.dp)
      .testTag(AccountSettingsTestTags.CARD_SIGNAL_LOGIN),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Image(
      painter = painterResource(R.drawable.image_signal_login_card_small),
      contentDescription = null,
      modifier = Modifier.size(width = 91.dp, height = 52.dp)
    )

    Text(
      text = stringResource(R.string.AccountSettingsFragment__account_and_recovery),
      style = MaterialTheme.typography.bodyLarge,
      modifier = Modifier
        .weight(1f)
        .padding(horizontal = 20.dp)
    )

    Icon(
      imageVector = SignalIcons.ChevronRight.imageVector,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

/**
 * Explanatory text shown underneath a section, ending in a "Learn more" link that has nowhere to go yet.
 */
@Composable
private fun SectionFooter(
  text: String,
  modifier: Modifier = Modifier
) {
  val learnMore = stringResource(R.string.AccountSettingsFragment__learn_more)
  val primaryColor = MaterialTheme.colorScheme.primary

  Text(
    text = remember(text, learnMore, primaryColor) {
      buildAnnotatedString {
        append(text)
        append(" ")
        withStyle(SpanStyle(color = primaryColor)) {
          append(learnMore)
        }
      }
    },
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = modifier.padding(horizontal = dimensionResource(CoreUiR.dimen.gutter), vertical = 16.dp)
  )
}

@Composable
private fun DeleteAllDataConfirmationDialog(
  onEvent: (AccountSettingsEvent) -> Unit
) {
  Dialogs.SimpleAlertDialog(
    title = stringResource(R.string.preferences_account_delete_all_data_confirmation_title),
    body = stringResource(R.string.preferences_account_delete_all_data_confirmation_message),
    confirm = stringResource(R.string.preferences_account_delete_all_data_confirmation_proceed),
    onConfirm = { onEvent(AccountSettingsEvent.DeleteAllDataConfirmed) },
    dismiss = stringResource(R.string.preferences_account_delete_all_data_confirmation_cancel),
    onDismissRequest = { onEvent(AccountSettingsEvent.DialogDismissed) },
    modifier = Modifier.testTag(AccountSettingsTestTags.DIALOG_CONFIRM_DELETE_ALL_DATA)
  )
}

@Composable
private fun ConfirmRemoveTotpAppDialog(
  onEvent: (AccountSettingsEvent) -> Unit
) {
  Dialogs.SimpleAlertDialog(
    title = stringResource(R.string.AccountSettingsFragment__remove_authenticator_app),
    body = stringResource(R.string.AccountSettingsFragment__you_wont_be_able_to_use_this_app),
    confirm = stringResource(R.string.AccountSettingsFragment__remove),
    onConfirm = { onEvent(AccountSettingsEvent.RemoveTotpAppConfirmed) },
    onDismiss = { onEvent(AccountSettingsEvent.DialogDismissed) },
    dismiss = stringResource(android.R.string.cancel),
    onDismissRequest = { onEvent(AccountSettingsEvent.DialogDismissed) },
    modifier = Modifier.testTag(AccountSettingsTestTags.DIALOG_CONFIRM_REMOVE_TOTP_APP)
  )
}

@Composable
private fun MaxTotpAppsReachedDialog(
  maxApps: Int,
  onEvent: (AccountSettingsEvent) -> Unit
) {
  Dialogs.SimpleAlertDialog(
    title = stringResource(R.string.AccountSettingsFragment__cant_add_authenticator_app),
    body = stringResource(R.string.AccountSettingsFragment__you_cant_add_more_than_d, maxApps),
    confirm = stringResource(android.R.string.ok),
    onConfirm = {},
    onDismiss = { onEvent(AccountSettingsEvent.DialogDismissed) },
    dismiss = stringResource(R.string.AccountSettingsFragment__learn_more),
    onDeny = { onEvent(AccountSettingsEvent.LearnMoreClicked) },
    onDismissRequest = { onEvent(AccountSettingsEvent.DialogDismissed) },
    modifier = Modifier.testTag(AccountSettingsTestTags.DIALOG_MAX_TOTP_APPS_REACHED)
  )
}

@Composable
private fun RegistrationLockConfirmationDialog(
  dialog: Dialog.ConfirmRegistrationLock,
  onEvent: (AccountSettingsEvent) -> Unit
) {
  Dialogs.BaseAlertDialog(
    onDismissRequest = { onEvent(AccountSettingsEvent.DialogDismissed) },
    modifier = Modifier.testTag(AccountSettingsTestTags.DIALOG_CONFIRM_REGISTRATION_LOCK),
    title = {
      Text(
        text = if (dialog.enable) {
          stringResource(R.string.RegistrationLockV2Dialog_turn_on_registration_lock)
        } else {
          stringResource(R.string.RegistrationLockV2Dialog_turn_off_registration_lock)
        }
      )
    },
    text = if (dialog.enable) {
      { Text(text = stringResource(R.string.RegistrationLockV2Dialog_if_you_forget_your_signal_pin_when_registering_again)) }
    } else {
      null
    },
    confirmButton = {
      TextButton(
        onClick = { onEvent(AccountSettingsEvent.RegistrationLockConfirmed) },
        modifier = Modifier.testTag(Dialogs.TEST_TAG_ALERT_DIALOG_CONFIRM_BUTTON)
      ) {
        Text(
          text = if (dialog.enable) {
            stringResource(R.string.RegistrationLockV2Dialog_turn_on)
          } else {
            stringResource(R.string.RegistrationLockV2Dialog_turn_off)
          }
        )
      }
    },
    dismissButton = {
      TextButton(
        onClick = { onEvent(AccountSettingsEvent.DialogDismissed) },
        modifier = Modifier.testTag(Dialogs.TEST_TAG_ALERT_DIALOG_DISMISS_BUTTON)
      ) {
        Text(text = stringResource(android.R.string.cancel))
      }
    }
  )
}

@Composable
private fun ConfirmPinToDisableRemindersDialog(
  dialog: Dialog.ConfirmPinToDisableReminders,
  onEvent: (AccountSettingsEvent) -> Unit
) {
  val focusRequester = remember { FocusRequester() }

  LaunchedEffect(Unit) {
    focusRequester.requestFocus()
  }

  Dialogs.BaseAlertDialog(
    onDismissRequest = { onEvent(AccountSettingsEvent.DialogDismissed) },
    modifier = Modifier.testTag(AccountSettingsTestTags.DIALOG_CONFIRM_PIN),
    title = { Text(text = stringResource(R.string.preferences_app_protection__confirm_your_signal_pin)) },
    text = {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          text = stringResource(R.string.preferences_app_protection__make_sure_you_memorize_or_securely_store_your_pin),
          textAlign = TextAlign.Center
        )

        TextField(
          value = dialog.pin,
          onValueChange = { onEvent(AccountSettingsEvent.PinEntryChanged(it)) },
          modifier = Modifier
            .padding(top = 24.dp)
            .focusRequester(focusRequester)
            .testTag(AccountSettingsTestTags.PIN_INPUT),
          placeholder = { Text(text = stringResource(R.string.preferences_app_protection__confirm_pin)) },
          singleLine = true,
          isError = dialog.incorrectPin,
          keyboardOptions = KeyboardOptions(
            keyboardType = if (dialog.isAlphanumericKeyboard) KeyboardType.Text else KeyboardType.Number,
            imeAction = ImeAction.Done
          ),
          keyboardActions = KeyboardActions(onDone = { if (dialog.canSubmit) onEvent(AccountSettingsEvent.DisablePinRemindersConfirmed) }),
          visualTransformation = PinVisualTransformation
        )

        TextButton(
          onClick = { onEvent(AccountSettingsEvent.PinKeyboardToggled) },
          modifier = Modifier.testTag(AccountSettingsTestTags.PIN_KEYBOARD_TOGGLE)
        ) {
          Icon(
            painter = if (dialog.isAlphanumericKeyboard) SignalIcons.NumberPad.painter else SignalIcons.Keyboard.painter,
            contentDescription = null,
            modifier = Modifier.padding(end = 8.dp)
          )
          Text(text = stringResource(R.string.RegistrationLockFragment__switch_keyboard))
        }

        if (dialog.incorrectPin) {
          Text(
            text = stringResource(R.string.preferences_app_protection__incorrect_pin_try_again),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
          )
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = { onEvent(AccountSettingsEvent.DisablePinRemindersConfirmed) },
        enabled = dialog.canSubmit,
        modifier = Modifier.testTag(Dialogs.TEST_TAG_ALERT_DIALOG_CONFIRM_BUTTON)
      ) {
        Text(text = stringResource(R.string.preferences_app_protection__turn_off))
      }
    },
    dismissButton = {
      TextButton(
        onClick = { onEvent(AccountSettingsEvent.DialogDismissed) },
        modifier = Modifier.testTag(Dialogs.TEST_TAG_ALERT_DIALOG_DISMISS_BUTTON)
      ) {
        Text(text = stringResource(android.R.string.cancel))
      }
    }
  )
}

@DayNightPreviews
@Composable
private fun AccountSettingsScreenPreview() {
  Previews.Preview {
    AccountSettingsScreen(
      state = AccountSettingsState(
        hasPin = true,
        hasRestoredAep = true,
        pinRemindersEnabled = true,
        registrationLockEnabled = true
      ),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun AccountSettingsScreenSignalLoginPreview() {
  Previews.Preview {
    AccountSettingsScreen(
      state = AccountSettingsState(
        isPhoneNumberless = true,
        signalLogin = AccountSettingsState.SignalLogin(
          twoFactorMethods = PREVIEW_TWO_FACTOR_METHODS,
          loadState = LoadState.LOADED,
          maxTotpApps = 2
        )
      ),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun AccountSettingsScreenDeprecatedPreview() {
  Previews.Preview {
    AccountSettingsScreen(
      state = AccountSettingsState(clientDeprecated = true),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun AccountSettingsScreenNoTwoFactorMethodsPreview() {
  Previews.Preview {
    AccountSettingsScreen(
      state = AccountSettingsState(
        isPhoneNumberless = true,
        signalLogin = AccountSettingsState.SignalLogin(loadState = LoadState.LOADED, maxTotpApps = 2)
      ),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun AccountSettingsScreenTwoFactorLoadFailedPreview() {
  Previews.Preview {
    AccountSettingsScreen(
      state = AccountSettingsState(
        isPhoneNumberless = true,
        signalLogin = AccountSettingsState.SignalLogin(loadState = LoadState.NETWORK_FAILURE, maxTotpApps = 2)
      ),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun DeleteAllDataConfirmationDialogPreview() {
  Previews.Preview {
    DeleteAllDataConfirmationDialog(onEvent = {})
  }
}

@DayNightPreviews
@Composable
private fun RegistrationLockConfirmationDialogPreview() {
  Previews.Preview {
    RegistrationLockConfirmationDialog(
      dialog = Dialog.ConfirmRegistrationLock(enable = true),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun ConfirmPinToDisableRemindersDialogPreview() {
  Previews.Preview {
    ConfirmPinToDisableRemindersDialog(
      dialog = Dialog.ConfirmPinToDisableReminders(pin = "1234", incorrectPin = true, canSubmit = true),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun ConfirmRemoveTotpAppDialogPreview() {
  Previews.Preview {
    ConfirmRemoveTotpAppDialog(onEvent = {})
  }
}

@DayNightPreviews
@Composable
private fun MaxTotpAppsReachedDialogPreview() {
  Previews.Preview {
    MaxTotpAppsReachedDialog(maxApps = 2, onEvent = {})
  }
}

private val PREVIEW_TWO_FACTOR_METHODS = listOf(
  TwoFactorMethod(id = 1, kind = TwoFactorMethod.Kind.AUTHENTICATOR_APP, name = "Bitwarden Authenticator", createdAt = System.currentTimeMillis()),
  TwoFactorMethod(id = 2, kind = TwoFactorMethod.Kind.AUTHENTICATOR_APP, name = "Twilio Authy", createdAt = System.currentTimeMillis()),
  TwoFactorMethod(id = 1, kind = TwoFactorMethod.Kind.PASSKEY, name = "Pixel Phone", createdAt = System.currentTimeMillis())
)
