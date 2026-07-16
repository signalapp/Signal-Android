/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.aepentry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.signal.registration.R
import org.signal.registration.fonts.MonoTypeface
import org.signal.registration.screens.OnePaneRegistrationScaffold
import org.signal.registration.screens.RegistrationScaffold
import org.signal.registration.screens.TwoPaneRegistrationScaffold
import org.signal.registration.screens.attachDebugLogHelper
import org.signal.registration.screens.localbackuprestore.attachBackupKeyAutoFillHelper
import org.signal.registration.screens.localbackuprestore.backupKeyAutoFillHelper
import org.signal.registration.test.TestTags
import org.signal.registration.util.RegistrationCredentialManager

@Composable
fun EnterAepScreen(
  state: EnterAepState,
  onEvent: (EnterAepEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  RegistrationErrorDialog(state.registrationError, onEvent)

  if (state.showDifferentAccountDialog) {
    DifferentAccountDialog(onEvent)
  }

  when (val layoutParams = RegistrationScaffold.rememberLayoutParams()) {
    is RegistrationScaffold.Params.OnePane -> OnePaneLayout(layoutParams, state, onEvent, modifier)
    is RegistrationScaffold.Params.TwoPane -> TwoPaneLayout(layoutParams, state, onEvent, modifier)
  }
}

/**
 * Warns that the entered key decrypts the backup but the backup was created by a different account, offering to
 * restore it anyway (which requires verifying the phone number over SMS first).
 */
@Composable
private fun DifferentAccountDialog(onEvent: (EnterAepEvents) -> Unit) {
  Dialogs.SimpleAlertDialog(
    title = stringResource(R.string.EnterAepScreen__restore_to_new_account),
    body = stringResource(R.string.EnterAepScreen__restore_to_new_account_body),
    confirm = stringResource(R.string.EnterAepScreen__restore),
    dismiss = stringResource(android.R.string.cancel),
    onConfirm = { onEvent(EnterAepEvents.ConfirmDifferentAccountRestore) },
    onDismiss = { onEvent(EnterAepEvents.DismissDifferentAccountDialog) }
  )
}

/**
 * Shows a dismissable dialog for generic registration errors (network/rate-limit/unknown). Incorrect-key errors are
 * surfaced inline on the text field instead, so they are intentionally not shown here.
 */
@Composable
private fun RegistrationErrorDialog(error: RegistrationError?, onEvent: (EnterAepEvents) -> Unit) {
  val message = when (error) {
    RegistrationError.NetworkError -> stringResource(R.string.VerificationCodeScreen__network_error)
    RegistrationError.RateLimited -> stringResource(R.string.VerificationCodeScreen__too_many_attempts)
    RegistrationError.UnknownError -> stringResource(R.string.VerificationCodeScreen__an_unexpected_error_occurred)
    RegistrationError.IncorrectRecoveryPassword, null -> null
  } ?: return

  Dialogs.SimpleMessageDialog(
    message = message,
    dismiss = stringResource(android.R.string.ok),
    onDismiss = { onEvent(EnterAepEvents.DismissError) }
  )
}

@Composable
private fun OnePaneLayout(
  params: RegistrationScaffold.Params.OnePane,
  state: EnterAepState,
  onEvent: (EnterAepEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  val scrollState = rememberScrollState()

  OnePaneRegistrationScaffold(
    modifier = modifier
      .fillMaxSize()
      .testTag(TestTags.ENTER_AEP_SCREEN),
    params = params,
    content = { paddingValues ->
      Column(
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(scrollState)
          .padding(paddingValues),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Description()
        Spacer(modifier = Modifier.size(24.dp))
        RecoveryKeyTextField(state, onEvent)
        if (state.isPasswordManagerAvailable) {
          FillFromPasswordManagerButton(onEvent)
        }
      }
    },
    footer = {
      RegistrationScaffold.FooterSurface(
        isElevated = scrollState.canScrollForward
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          horizontalArrangement = Arrangement.SpaceEvenly
        ) {
          Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
          ) {
            NoRecoverKeyButton(onEvent)
          }
          Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterEnd
          ) {
            NextButton(state, onEvent)
          }
        }
      }
    }
  )
}

@Composable
private fun TwoPaneLayout(
  params: RegistrationScaffold.Params.TwoPane,
  state: EnterAepState,
  onEvent: (EnterAepEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  val firstPaneScrollState = rememberScrollState()
  val secondPaneScrollState = rememberScrollState()

  TwoPaneRegistrationScaffold(
    modifier = modifier
      .fillMaxSize()
      .testTag(TestTags.ENTER_AEP_SCREEN),
    params = params,
    firstPane = { paddingValues ->
      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
          .verticalScroll(firstPaneScrollState)
          .padding(paddingValues)
      ) {
        Description()
      }
    },
    secondPane = { paddingValues ->
      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
          .verticalScroll(secondPaneScrollState)
          .padding(paddingValues)
      ) {
        RecoveryKeyTextField(state, onEvent)
        if (state.isPasswordManagerAvailable) {
          FillFromPasswordManagerButton(onEvent)
        }
      }
    },
    footer = {
      RegistrationScaffold.FooterSurface(
        isElevated = firstPaneScrollState.canScrollForward || secondPaneScrollState.canScrollForward
      ) {
        Row(
          horizontalArrangement = Arrangement.End,
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
        ) {
          NoRecoverKeyButton(onEvent)
          Spacer(modifier = Modifier.size(24.dp))
          NextButton(state, onEvent)
        }
      }
    }
  )
}

@Composable
private fun Description() {
  Text(
    text = stringResource(R.string.EnterAepScreen__enter_your_recovery_key),
    style = MaterialTheme.typography.headlineMedium,
    modifier = Modifier
      .fillMaxWidth()
      .attachDebugLogHelper()
  )

  Text(
    text = stringResource(R.string.EnterAepScreen__your_recovery_key_is_a_64_character_code),
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 16.dp)
  )
}

@Composable
private fun RecoveryKeyTextField(state: EnterAepState, onEvent: (EnterAepEvents) -> Unit) {
  val visualTransform = remember(state.chunkLength) { AepVisualTransformation(state.chunkLength) }
  val focusRequester = remember { FocusRequester() }
  var requestFocus by remember { mutableStateOf(true) }
  val keyboardController = LocalSoftwareKeyboardController.current
  val autoFillHelper = backupKeyAutoFillHelper { onEvent(EnterAepEvents.BackupKeyChanged(it)) }

  TextField(
    value = state.enteredText,
    onValueChange = {
      onEvent(EnterAepEvents.BackupKeyChanged(it))
      autoFillHelper.onValueChanged(it)
    },
    label = { Text(stringResource(R.string.EnterAepScreen__recovery_key)) },
    textStyle = MaterialTheme.typography.bodyLarge.copy(
      fontFamily = MonoTypeface.fontFamily(),
      lineHeight = 36.sp
    ),
    colors = TextFieldDefaults.colors(
      unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
      focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
      errorContainerColor = MaterialTheme.colorScheme.surfaceVariant
    ),
    keyboardOptions = KeyboardOptions(
      keyboardType = KeyboardType.Password,
      capitalization = KeyboardCapitalization.None,
      imeAction = ImeAction.Next,
      autoCorrectEnabled = false
    ),
    keyboardActions = KeyboardActions(
      onNext = {
        if (state.isBackupKeyValid) {
          keyboardController?.hide()
          onEvent(EnterAepEvents.Submit)
        }
      }
    ),
    supportingText = {
      when (val error = state.aepValidationError) {
        is AepValidationError.TooLong -> Text(stringResource(R.string.EnterAepScreen__too_long, error.count, error.max))
        is AepValidationError.Invalid -> Text(stringResource(R.string.EnterAepScreen__invalid_recovery_key))
        is AepValidationError.Incorrect -> Text(stringResource(R.string.EnterAepScreen__incorrect_recovery_key))
        null -> {}
      }
    },
    isError = state.aepValidationError != null,
    minLines = 4,
    visualTransformation = visualTransform,
    modifier = Modifier
      .fillMaxWidth()
      .testTag(TestTags.ENTER_AEP_INPUT)
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

@Composable
private fun FillFromPasswordManagerButton(onEvent: (EnterAepEvents) -> Unit, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()

  Buttons.MediumTonal(
    modifier = modifier,
    onClick = {
      coroutineScope.launch {
        val password = RegistrationCredentialManager.getPasswordCredential(context)
        if (password != null) {
          onEvent(EnterAepEvents.BackupKeyChanged(password))
        }
      }
    }
  ) {
    Text(text = stringResource(R.string.EnterAepScreen__fill_from_password_manager))
  }
}

@Composable
private fun NoRecoverKeyButton(onEvent: (EnterAepEvents) -> Unit, modifier: Modifier = Modifier) {
  TextButton(
    modifier = modifier.testTag(TestTags.ENTER_AEP_NO_KEY_BUTTON),
    shape = RoundedCornerShape(0.dp),
    onClick = { onEvent(EnterAepEvents.Cancel) }
  ) {
    Text(text = stringResource(R.string.EnterAepScreen__no_recovery_key))
  }
}

@Composable
private fun NextButton(state: EnterAepState, onEvent: (EnterAepEvents) -> Unit, modifier: Modifier = Modifier) {
  Buttons.LargeTonal(
    modifier = modifier.testTag(TestTags.ENTER_AEP_NEXT_BUTTON),
    enabled = state.isBackupKeyValid && state.aepValidationError == null && !state.isRegistering,
    onClick = { onEvent(EnterAepEvents.Submit) }
  ) {
    if (state.isRegistering) {
      CircularProgressIndicator(
        modifier = Modifier.size(24.dp),
        strokeWidth = 3.dp,
        color = MaterialTheme.colorScheme.primary
      )
    } else {
      Text(text = stringResource(R.string.LocalBackupRestoreScreen__next))
    }
  }
}

/**
 * Visual formatter for backup keys. Uppercases and groups characters with spaces without swapping
 * display-equivalent characters.
 */
internal class AepVisualTransformation(private val chunkSize: Int) : VisualTransformation {
  override fun filter(text: AnnotatedString): TransformedText {
    var output = ""
    for ((i, c) in text.text.withIndex()) {
      output += c
      if (i % chunkSize == chunkSize - 1) {
        output += " "
      }
    }

    val transformed = output.trimEnd().uppercase()

    return TransformedText(
      text = AnnotatedString(transformed),
      offsetMapping = AepOffsetMapping(chunkSize, text.length)
    )
  }

  private class AepOffsetMapping(private val chunkSize: Int, private val inputSize: Int) : OffsetMapping {
    override fun originalToTransformed(offset: Int): Int {
      val transformed = offset + (offset / chunkSize)
      return when {
        inputSize == 0 -> 0
        offset == inputSize && offset >= chunkSize && offset % chunkSize == 0 -> transformed - 1
        else -> transformed
      }
    }

    override fun transformedToOriginal(offset: Int): Int {
      return offset - (offset / (chunkSize + 1))
    }
  }
}

@AllDevicePreviews
@Composable
private fun EnterAepScreenPreview() {
  Previews.Preview {
    EnterAepScreen(
      state = EnterAepState(isPasswordManagerAvailable = true),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun EnterAepScreenFilledPreview() {
  Previews.Preview {
    EnterAepScreen(
      state = EnterAepState(
        enteredText = "uy38jh2778hjjhj8lk19ga61s672jsj089r023s6a57809bap92j2yh5t326vv7t",
        backupKey = "uy38jh2778hjjhj8lk19ga61s672jsj089r023s6a57809bap92j2yh5t326vv7t",
        isBackupKeyValid = true,
        isPasswordManagerAvailable = true
      ),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun EnterAepScreenLoadingPreview() {
  Previews.Preview {
    EnterAepScreen(
      state = EnterAepState(
        enteredText = "uy38jh2778hjjhj8lk19ga61s672jsj089r023s6a57809bap92j2yh5t326vv7t",
        backupKey = "uy38jh2778hjjhj8lk19ga61s672jsj089r023s6a57809bap92j2yh5t326vv7t",
        isBackupKeyValid = true,
        isRegistering = true,
        isPasswordManagerAvailable = true
      ),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun EnterAepScreenErrorPreview() {
  Previews.Preview {
    EnterAepScreen(
      state = EnterAepState(
        enteredText = "uy38jh2778hjjhj8lk19ga61s672jsj089r023s6a57809bap92j2yh5t326vv7t",
        backupKey = "uy38jh2778hjjhj8lk19ga61s672jsj089r023s6a57809bap92j2yh5t326vv7t",
        isBackupKeyValid = false,
        aepValidationError = AepValidationError.Invalid,
        isPasswordManagerAvailable = true
      ),
      onEvent = {}
    )
  }
}
