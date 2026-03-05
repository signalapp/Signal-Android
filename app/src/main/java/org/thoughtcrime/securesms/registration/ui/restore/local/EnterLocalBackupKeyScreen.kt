/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.restore.local

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.CircularProgressWrapper
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.fonts.MonoTypeface
import org.thoughtcrime.securesms.registration.data.network.RegisterAccountResult
import org.thoughtcrime.securesms.registration.ui.restore.AccountEntropyPoolVerification
import org.thoughtcrime.securesms.registration.ui.restore.BackupKeyVisualTransformation
import org.thoughtcrime.securesms.registration.ui.restore.RegistrationErrorDialogs
import org.thoughtcrime.securesms.registration.ui.restore.attachBackupKeyAutoFillHelper
import org.thoughtcrime.securesms.registration.ui.restore.backupKeyAutoFillHelper
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationScreen

@Composable
fun EnterLocalBackupKeyScreen(
  backupKey: String,
  isRegistrationInProgress: Boolean,
  isBackupKeyValid: Boolean,
  aepValidationError: AccountEntropyPoolVerification.AEPValidationError?,
  onBackupKeyChanged: (String) -> Unit,
  onNextClicked: () -> Unit,
  onNoBackupKeyClick: () -> Unit,
  showRegistrationError: Boolean = false,
  registerAccountResult: RegisterAccountResult? = null,
  onRegistrationErrorDismiss: () -> Unit = {},
  onBackupKeyHelp: () -> Unit = {}
) {
  val visualTransform = remember { BackupKeyVisualTransformation(chunkSize = 4) }
  val keyboardController = LocalSoftwareKeyboardController.current
  val focusRequester = remember { FocusRequester() }
  var requestFocus by remember { mutableStateOf(true) }

  val autoFillHelper = backupKeyAutoFillHelper { onBackupKeyChanged(it) }

  RegistrationScreen(
    title = stringResource(R.string.EnterLocalBackupKeyScreen__enter_your_recovery_key),
    subtitle = stringResource(R.string.EnterLocalBackupKeyScreen__your_recovery_key_is_a_64_character_code),
    bottomContent = {
      Row {
        TextButton(
          onClick = onNoBackupKeyClick,
          colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.outline)
        ) {
          Text(text = stringResource(R.string.EnterLocalBackupKeyScreen__no_backup_key))
        }
        Spacer(modifier = Modifier.weight(1f))
        CircularProgressWrapper(
          isLoading = isRegistrationInProgress
        ) {
          Buttons.LargeTonal(
            onClick = onNextClicked,
            enabled = isBackupKeyValid && aepValidationError == null
          ) {
            Text(text = stringResource(R.string.EnterLocalBackupKeyScreen__next))
          }
        }
      }
    }
  ) {
    TextField(
      value = backupKey,
      onValueChange = { value ->
        onBackupKeyChanged(value)
        autoFillHelper.onValueChanged(value)
      },
      label = {
        Text(text = stringResource(id = R.string.EnterBackupKey_backup_key))
      },
      textStyle = LocalTextStyle.current.copy(
        fontFamily = MonoTypeface.fontFamily(),
        lineHeight = 36.sp
      ),
      keyboardOptions = KeyboardOptions(
        keyboardType = KeyboardType.Password,
        capitalization = KeyboardCapitalization.None,
        imeAction = ImeAction.Done,
        autoCorrectEnabled = false
      ),
      keyboardActions = KeyboardActions(
        onDone = {
          if (isBackupKeyValid && aepValidationError == null) {
            keyboardController?.hide()
            onNextClicked()
          }
        }
      ),
      supportingText = { aepValidationError?.let { ValidationErrorMessage(it) } },
      isError = aepValidationError != null,
      minLines = 4,
      visualTransformation = visualTransform,
      modifier = Modifier
        .fillMaxWidth()
        .focusRequester(focusRequester)
        .attachBackupKeyAutoFillHelper(autoFillHelper)
        .onGloballyPositioned {
          if (requestFocus) {
            focusRequester.requestFocus()
            requestFocus = false
          }
        }
    )

    RegistrationErrorDialogs(
      showRegistrationError = showRegistrationError,
      registerAccountResult = registerAccountResult,
      onRegistrationErrorDismiss = onRegistrationErrorDismiss,
      onBackupKeyHelp = onBackupKeyHelp
    )
  }
}

@Composable
private fun ValidationErrorMessage(error: AccountEntropyPoolVerification.AEPValidationError) {
  when (error) {
    is AccountEntropyPoolVerification.AEPValidationError.TooLong -> Text(text = stringResource(R.string.EnterBackupKey_too_long_error, error.count, error.max))
    AccountEntropyPoolVerification.AEPValidationError.Invalid -> Text(text = stringResource(R.string.EnterBackupKey_invalid_backup_key_error))
    AccountEntropyPoolVerification.AEPValidationError.Incorrect -> Text(text = stringResource(R.string.EnterBackupKey_incorrect_backup_key_error))
  }
}

@DayNightPreviews
@Composable
private fun EnterLocalBackupKeyScreenPreview() {
  Previews.Preview {
    EnterLocalBackupKeyScreen(
      backupKey = "",
      isRegistrationInProgress = false,
      isBackupKeyValid = false,
      aepValidationError = null,
      onBackupKeyChanged = {},
      onNextClicked = {},
      onNoBackupKeyClick = {}
    )
  }
}
