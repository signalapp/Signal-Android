package org.thoughtcrime.securesms.backup.v2.ui.subscription

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.horizontalGutters
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.fonts.MonoTypeface
import org.thoughtcrime.securesms.registrationv3.ui.restore.BackupKeyVisualTransformation
import org.thoughtcrime.securesms.registrationv3.ui.restore.attachBackupKeyAutoFillHelper
import org.thoughtcrime.securesms.registrationv3.ui.restore.backupKeyAutoFillHelper
import org.whispersystems.signalservice.api.AccountEntropyPool

/**
 * Screen to enter backup key with an option to view the backup key again
 */
@Composable
fun EnterKeyScreen(
  paddingValues: PaddingValues,
  backupKey: String,
  onNextClick: () -> Unit,
  captionContent: @Composable () -> Unit,
  seeKeyButton: @Composable () -> Unit
) {
  Column(
    verticalArrangement = Arrangement.SpaceBetween,
    modifier = Modifier
      .padding(paddingValues)
      .consumeWindowInsets(paddingValues)
      .imePadding()
      .fillMaxSize()
  ) {
    val scrollState = rememberScrollState()

    val focusRequester = remember { FocusRequester() }
    val visualTransform = remember { BackupKeyVisualTransformation(chunkSize = 4) }
    val keyboardController = LocalSoftwareKeyboardController.current

    var enteredBackupKey by remember { mutableStateOf("") }
    var isBackupKeyValid by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }

    Column(
      modifier = Modifier
        .verticalScroll(scrollState)
        .weight(weight = 1f, fill = false)
        .horizontalGutters(),
      horizontalAlignment = Alignment.Start
    ) {
      captionContent()

      Spacer(modifier = Modifier.height(48.dp))

      val updateEnteredBackupKey = { input: String ->
        enteredBackupKey = AccountEntropyPool.removeIllegalCharacters(input).uppercase()
        isBackupKeyValid = enteredBackupKey == backupKey
        showError = !isBackupKeyValid && enteredBackupKey.length >= backupKey.length
      }

      var requestFocus: Boolean by remember { mutableStateOf(true) }
      val autoFillHelper = backupKeyAutoFillHelper { updateEnteredBackupKey(it) }

      TextField(
        value = enteredBackupKey,
        onValueChange = {
          updateEnteredBackupKey(it)
          autoFillHelper.onValueChanged(it)
        },
        label = {
          Text(text = stringResource(id = R.string.MessageBackupsKeyVerifyScreen__backup_key))
        },
        textStyle = LocalTextStyle.current.copy(
          fontFamily = MonoTypeface.fontFamily(),
          lineHeight = 36.sp
        ),
        keyboardOptions = KeyboardOptions(
          keyboardType = KeyboardType.Password,
          capitalization = KeyboardCapitalization.None,
          imeAction = ImeAction.Next,
          autoCorrectEnabled = false
        ),
        keyboardActions = KeyboardActions(
          onNext = {
            if (isBackupKeyValid) {
              keyboardController?.hide()
              onNextClick()
            }
          }
        ),
        colors = TextFieldDefaults.colors(
          focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
          unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        supportingText = { if (showError) Text(text = stringResource(R.string.MessageBackupsKeyVerifyScreen__incorrect_backup_key)) },
        isError = showError,
        minLines = 4,
        visualTransformation = visualTransform,
        modifier = Modifier
          .testTag("message-backups-key-verify-screen-backup-key-input-field")
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
    }

    Surface(
      shadowElevation = if (scrollState.canScrollForward) 8.dp else 0.dp,
      modifier = Modifier.fillMaxWidth()
    ) {
      Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
          .padding(top = 8.dp, bottom = 24.dp)
          .horizontalGutters()
          .fillMaxWidth()
      ) {
        seeKeyButton()

        Buttons.LargeTonal(
          enabled = isBackupKeyValid,
          onClick = onNextClick
        ) {
          Text(
            text = stringResource(id = R.string.RegistrationActivity_next)
          )
        }
      }
    }
  }
}
