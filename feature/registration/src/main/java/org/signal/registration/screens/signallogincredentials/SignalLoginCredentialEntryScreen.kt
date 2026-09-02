/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signallogincredentials

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.autofill.contentType
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalIcons
import org.signal.passwordmanager.SignalCredentialManager
import org.signal.passwordmanager.compose.attachPasswordAutoFillHelper
import org.signal.passwordmanager.compose.passwordAutoFillHelper
import org.signal.registration.R
import org.signal.registration.fonts.MonoTypeface
import org.signal.registration.screens.OnePaneRegistrationScaffold
import org.signal.registration.screens.RegistrationScaffold
import org.signal.registration.screens.TwoPaneRegistrationScaffold
import org.signal.registration.screens.aepentry.AepInput
import org.signal.registration.screens.aepentry.AepValidationError
import org.signal.registration.screens.aepentry.AepVisualTransformation
import org.signal.registration.screens.attachDebugLogHelper
import org.signal.registration.screens.shared.AccountIdErrorText
import org.signal.registration.screens.shared.AccountIdVisualTransformation
import org.signal.registration.screens.shared.BackTopAppBar
import org.signal.registration.screens.shared.accountIdTextStyle
import org.signal.registration.test.TestTags

/** How the recovery key is grouped when it is spelled out rather than masked. */
private const val RECOVERY_KEY_CHUNK_LENGTH = 4

/**
 * Collects an existing Signal Login -- the account ID and the recovery key that pairs with it -- and logs the user
 * back in with the two together.
 */
@Composable
fun SignalLoginCredentialEntryScreen(
  state: SignalLoginCredentialEntryState,
  onEvent: (SignalLoginCredentialEntryScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  LoginErrorDialog(state.loginError, onEvent)

  Surface(
    modifier = modifier
      .fillMaxSize()
      .testTag(TestTags.SIGNAL_LOGIN_CREDENTIAL_ENTRY_SCREEN)
  ) {
    when (val params = RegistrationScaffold.rememberLayoutParams()) {
      is RegistrationScaffold.Params.OnePane -> OnePaneLayout(params, state, onEvent)
      is RegistrationScaffold.Params.TwoPane -> TwoPaneLayout(params, state, onEvent)
    }
  }
}

/**
 * Shows a dismissable dialog for the login failures the text fields can't express. A rejected pair is surfaced inline
 * on both fields instead, since either half could be the one at fault.
 */
@Composable
private fun LoginErrorDialog(error: SignalLoginError?, onEvent: (SignalLoginCredentialEntryScreenEvents) -> Unit) {
  val message = when (error) {
    SignalLoginError.NetworkError -> stringResource(R.string.VerificationCodeScreen__network_error)
    SignalLoginError.RateLimited -> stringResource(R.string.VerificationCodeScreen__too_many_attempts)
    SignalLoginError.UnknownError -> stringResource(R.string.VerificationCodeScreen__an_unexpected_error_occurred)
    null -> null
  } ?: return

  Dialogs.SimpleMessageDialog(
    message = message,
    dismiss = stringResource(android.R.string.ok),
    onDismiss = { onEvent(SignalLoginCredentialEntryScreenEvents.DismissError) }
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnePaneLayout(
  params: RegistrationScaffold.Params.OnePane,
  state: SignalLoginCredentialEntryState,
  onEvent: (SignalLoginCredentialEntryScreenEvents) -> Unit
) {
  val scrollState = rememberScrollState()
  val topBarScrollBehavior = RegistrationScaffold.rememberTopBarScrollBehavior()

  OnePaneRegistrationScaffold(
    params = params,
    topBar = { BackTopAppBar(scrollBehavior = topBarScrollBehavior, onBackClick = { onEvent(SignalLoginCredentialEntryScreenEvents.BackClicked) }) },
    content = { paddingValues ->
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .fillMaxSize()
          .nestedScroll(topBarScrollBehavior.nestedScrollConnection)
          .verticalScroll(scrollState)
          .padding(paddingValues)
      ) {
        Header()

        Spacer(modifier = Modifier.height(32.dp))

        CredentialTextFields(state = state, onEvent = onEvent)
      }
    },
    footer = { Footer(params, state, scrollState.canScrollForward, onEvent) }
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TwoPaneLayout(
  params: RegistrationScaffold.Params.TwoPane,
  state: SignalLoginCredentialEntryState,
  onEvent: (SignalLoginCredentialEntryScreenEvents) -> Unit
) {
  val firstPaneScrollState = rememberScrollState()
  val secondPaneScrollState = rememberScrollState()
  val topBarScrollBehavior = RegistrationScaffold.rememberTopBarScrollBehavior()

  TwoPaneRegistrationScaffold(
    params = params,
    topBar = { BackTopAppBar(scrollBehavior = topBarScrollBehavior, onBackClick = { onEvent(SignalLoginCredentialEntryScreenEvents.BackClicked) }) },
    firstPane = { paddingValues ->
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
          .nestedScroll(topBarScrollBehavior.nestedScrollConnection)
          .verticalScroll(firstPaneScrollState)
          .padding(paddingValues)
      ) {
        Header(twoPane = true)
      }
    },
    secondPane = { paddingValues ->
      Column(
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
          .nestedScroll(topBarScrollBehavior.nestedScrollConnection)
          .verticalScroll(secondPaneScrollState)
          .padding(paddingValues)
      ) {
        CredentialTextFields(state = state, onEvent = onEvent)
      }
    },
    footer = { Footer(params, state, firstPaneScrollState.canScrollForward || secondPaneScrollState.canScrollForward, onEvent) }
  )
}

@Composable
private fun Header(twoPane: Boolean = false) {
  Image(
    painter = painterResource(R.drawable.image_signal_login_ring),
    contentDescription = null,
    modifier = Modifier.size(64.dp)
  )

  Spacer(modifier = Modifier.height(20.dp))

  Text(
    text = stringResource(R.string.SignalLoginCredentialEntryScreen__signal_login),
    style = if (twoPane) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium,
    textAlign = TextAlign.Center,
    modifier = Modifier
      .fillMaxWidth()
      .attachDebugLogHelper()
  )

  Spacer(modifier = Modifier.height(12.dp))

  Text(
    text = stringResource(R.string.SignalLoginCredentialEntryScreen__enter_your_account_id_followed_by_your_recovery_key),
    style = if (twoPane) MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal) else MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Center,
    modifier = Modifier.fillMaxWidth()
  )
}

@Composable
private fun CredentialTextFields(
  state: SignalLoginCredentialEntryState,
  onEvent: (SignalLoginCredentialEntryScreenEvents) -> Unit
) {
  val passwordManagerPrompt = passwordManagerPromptOnFocus(state, onEvent)

  AccountIdTextField(state = state, onEvent = onEvent, modifier = passwordManagerPrompt)

  Spacer(modifier = Modifier.height(12.dp))

  RecoveryKeyTextField(state = state, onEvent = onEvent, modifier = passwordManagerPrompt)
}

/**
 * Builds a modifier that prompts the password manager the first time either credential field is tapped, so a saved
 * login can fill both halves at once. Only fires while the user hasn't filled anything in themselves, and only once
 * per screen so a dismissed prompt doesn't keep coming back.
 */
@Composable
private fun passwordManagerPromptOnFocus(
  state: SignalLoginCredentialEntryState,
  onEvent: (SignalLoginCredentialEntryScreenEvents) -> Unit
): Modifier {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  var hasPrompted by rememberSaveable { mutableStateOf(false) }

  return Modifier.onFocusChanged { focusState ->
    if (focusState.isFocused && !hasPrompted && state.canPromptPasswordManager && SignalCredentialManager.isSupported(context)) {
      hasPrompted = true
      coroutineScope.launch {
        val credential = SignalCredentialManager.getCredential(context)
        if (credential != null) {
          onEvent(SignalLoginCredentialEntryScreenEvents.PasswordManagerCredentialSelected(accountId = credential.username, recoveryKey = credential.password))
        }
      }
    }
  }
}

@Composable
private fun AccountIdTextField(
  state: SignalLoginCredentialEntryState,
  onEvent: (SignalLoginCredentialEntryScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  TextField(
    value = state.accountId,
    onValueChange = { onEvent(SignalLoginCredentialEntryScreenEvents.AccountIdChanged(it)) },
    label = { Text(stringResource(R.string.SignalLoginCredentialEntryScreen__account_id)) },
    singleLine = true,
    textStyle = accountIdTextStyle(),
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
    supportingText = {
      state.accountIdError?.let { AccountIdErrorText(it) }
    },
    isError = state.accountIdError != null || state.areCredentialsIncorrect,
    visualTransformation = AccountIdVisualTransformation,
    modifier = modifier
      .fillMaxWidth()
      .contentType(ContentType.Username)
      .testTag(TestTags.SIGNAL_LOGIN_CREDENTIAL_ACCOUNT_ID_FIELD)
  )
}

@Composable
private fun RecoveryKeyTextField(
  state: SignalLoginCredentialEntryState,
  onEvent: (SignalLoginCredentialEntryScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  val keyboardController = LocalSoftwareKeyboardController.current
  val autoFillHelper = passwordAutoFillHelper { onEvent(SignalLoginCredentialEntryScreenEvents.RecoveryKeyChanged(it)) }
  val revealed = state.isRecoveryKeyRevealed
  val visualTransformation = remember(revealed) {
    if (revealed) AepVisualTransformation(RECOVERY_KEY_CHUNK_LENGTH) else PasswordVisualTransformation()
  }

  TextField(
    value = state.recoveryKey.enteredText,
    onValueChange = {
      onEvent(SignalLoginCredentialEntryScreenEvents.RecoveryKeyChanged(it))
      autoFillHelper.onValueChanged(it)
    },
    label = { Text(stringResource(R.string.SignalLoginCredentialEntryScreen__recovery_key)) },
    singleLine = !revealed,
    minLines = if (revealed) 3 else 1,
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
      imeAction = ImeAction.Done,
      autoCorrectEnabled = false
    ),
    keyboardActions = KeyboardActions(
      onDone = {
        if (state.isNextEnabled) {
          keyboardController?.hide()
          onEvent(SignalLoginCredentialEntryScreenEvents.NextClicked)
        }
      }
    ),
    trailingIcon = { RevealRecoveryKeyButton(revealed, onEvent) },
    supportingText = {
      val error = state.recoveryKey.error
      when {
        state.areCredentialsIncorrect -> Text(stringResource(R.string.SignalLoginCredentialEntryScreen__incorrect_account_id_or_recovery_key))
        error is AepValidationError.TooLong -> Text(stringResource(R.string.EnterAepScreen__too_long, error.count, error.max))
        error != null -> Text(stringResource(R.string.EnterAepScreen__invalid_recovery_key))
      }
    },
    isError = state.recoveryKey.error != null || state.areCredentialsIncorrect,
    visualTransformation = visualTransformation,
    modifier = modifier
      .fillMaxWidth()
      .contentType(ContentType.Password)
      .testTag(TestTags.SIGNAL_LOGIN_CREDENTIAL_RECOVERY_KEY_FIELD)
      .attachPasswordAutoFillHelper(autoFillHelper)
  )
}

@Composable
private fun RevealRecoveryKeyButton(revealed: Boolean, onEvent: (SignalLoginCredentialEntryScreenEvents) -> Unit) {
  IconButton(
    onClick = { onEvent(SignalLoginCredentialEntryScreenEvents.RecoveryKeyVisibilityToggled) },
    modifier = Modifier.testTag(TestTags.SIGNAL_LOGIN_CREDENTIAL_REVEAL_RECOVERY_KEY_BUTTON)
  ) {
    Icon(
      painter = if (revealed) SignalIcons.VisibleSlash.painter else SignalIcons.Visible.painter,
      contentDescription = stringResource(
        if (revealed) R.string.SignalLoginCredentialEntryScreen__hide_recovery_key else R.string.SignalLoginCredentialEntryScreen__show_recovery_key
      )
    )
  }
}

@Composable
private fun Footer(
  params: RegistrationScaffold.Params,
  state: SignalLoginCredentialEntryState,
  isElevated: Boolean,
  onEvent: (SignalLoginCredentialEntryScreenEvents) -> Unit
) {
  RegistrationScaffold.FooterSurface(isElevated = isElevated) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .fillMaxWidth()
        .padding(params.footerPadding)
    ) {
      Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier.weight(1f)
      ) {
        NeedHelpButton(onEvent)
      }

      Box(
        contentAlignment = Alignment.CenterEnd,
        modifier = Modifier.weight(1f)
      ) {
        NextButton(state, onEvent)
      }
    }
  }
}

@Composable
private fun NeedHelpButton(onEvent: (SignalLoginCredentialEntryScreenEvents) -> Unit) {
  TextButton(
    shape = RoundedCornerShape(0.dp),
    onClick = { onEvent(SignalLoginCredentialEntryScreenEvents.NeedHelpClicked) },
    modifier = Modifier.testTag(TestTags.SIGNAL_LOGIN_CREDENTIAL_NEED_HELP_BUTTON)
  ) {
    Text(text = stringResource(R.string.SignalLoginCredentialEntryScreen__need_help))
  }
}

@Composable
private fun NextButton(state: SignalLoginCredentialEntryState, onEvent: (SignalLoginCredentialEntryScreenEvents) -> Unit) {
  Buttons.LargeTonal(
    enabled = state.isNextEnabled,
    onClick = { onEvent(SignalLoginCredentialEntryScreenEvents.NextClicked) },
    modifier = Modifier.testTag(TestTags.SIGNAL_LOGIN_CREDENTIAL_NEXT_BUTTON)
  ) {
    if (state.isLoggingIn) {
      CircularProgressIndicator(
        modifier = Modifier.size(24.dp),
        strokeWidth = 3.dp,
        color = MaterialTheme.colorScheme.primary
      )
    } else {
      Text(text = stringResource(R.string.SignalLoginCredentialEntryScreen__next))
    }
  }
}

@AllDevicePreviews
@Composable
private fun SignalLoginCredentialEntryScreenPreview() {
  Previews.Preview {
    SignalLoginCredentialEntryScreen(
      state = SignalLoginCredentialEntryState(),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun SignalLoginCredentialEntryScreenFilledPreview() {
  Previews.Preview {
    SignalLoginCredentialEntryScreen(
      state = SignalLoginCredentialEntryState(
        accountId = "a6b284822e3283d07f2391360a4c2b91",
        recoveryKey = AepInput.from("uy38jh2778hjjhj8lk19ga61s672jsj089r023s6a57809bap92j2yh5t326vv7t")
      ),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun SignalLoginCredentialEntryScreenRevealedPreview() {
  Previews.Preview {
    SignalLoginCredentialEntryScreen(
      state = SignalLoginCredentialEntryState(
        accountId = "a6b284822e3283d07f2391360a4c2b91",
        recoveryKey = AepInput.from("uy38jh2778hjjhj8lk19ga61s672jsj089r023s6a57809bap92j2yh5t326vv7t"),
        isRecoveryKeyRevealed = true
      ),
      onEvent = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun SignalLoginCredentialEntryScreenErrorPreview() {
  Previews.Preview {
    SignalLoginCredentialEntryScreen(
      state = SignalLoginCredentialEntryState(
        accountId = "a6b284822e3283d07f2391360a4c2b91",
        recoveryKey = AepInput.from("uy38jh2778hjjhj8lk19ga61s672jsj089r023s6a57809bap92j2yh5t326vv7t"),
        areCredentialsIncorrect = true
      ),
      onEvent = {}
    )
  }
}
