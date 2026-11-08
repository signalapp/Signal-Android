/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.twofactorverify

import androidx.activity.compose.BackHandler
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.signal.appsettings.R
import org.signal.appsettings.appSettingsScreen
import org.signal.appsettings.twofactorverify.TwoFactorVerifyState.CodeError
import org.signal.appsettings.twofactorverify.TwoFactorVerifyState.Dialog
import org.signal.appsettings.twofactorverify.TwoFactorVerifyState.Step
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalIcons
import org.signal.uicomponents.codeentryfield.CodeEntryField
import org.signal.uicomponents.codeentryfield.CodeEntryFieldState

@VisibleForTesting
object TwoFactorVerifyTestTags {
  const val LOADING = "loading"
  const val PASSKEY_OPTION = "passkey-option"
  const val AUTHENTICATOR_APP_OPTION = "authenticator-app-option"
  const val CODE_ERROR = "code-error"
  const val BUTTON_CANCEL = "button-cancel"
  const val DIALOG_PASSKEY_FAILED = "dialog-passkey-failed"
  const val DIALOG_NETWORK_FAILURE = "dialog-network-failure"
}

/**
 * Walks the user through proving they still hold one of their two-factor methods, in answer to the reminder megaphone.
 * Starts by finding out what the account has, then either lets them choose a method or goes straight to collecting a
 * code when an authenticator app is all there is.
 */
@Composable
fun TwoFactorVerifyScreen(
  state: TwoFactorVerifyState,
  onEvent: (TwoFactorVerifyEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  BackHandler { onEvent(TwoFactorVerifyEvent.BackClicked) }

  Scaffold(
    bottomBar = {
      Row(
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
          .fillMaxWidth()
          .navigationBarsPadding()
          .imePadding()
          .padding(16.dp)
      ) {
        TextButton(
          onClick = { onEvent(TwoFactorVerifyEvent.CancelClicked) },
          modifier = Modifier.testTag(TwoFactorVerifyTestTags.BUTTON_CANCEL)
        ) {
          Text(
            text = stringResource(R.string.TwoFactorVerifyScreen__cancel),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge
          )
        }
      }
    },
    modifier = modifier
  ) { contentPadding ->
    when (val step = state.step) {
      Step.Loading -> {
        Loading(modifier = Modifier.padding(contentPadding))
      }
      is Step.ChooseMethod -> {
        ChooseMethod(
          step = step,
          onEvent = onEvent,
          modifier = Modifier.padding(contentPadding)
        )
      }
      is Step.EnterCode -> {
        EnterCode(
          state = state,
          onEvent = onEvent,
          modifier = Modifier.padding(contentPadding)
        )
      }
    }
  }

  when (state.dialog) {
    Dialog.None -> Unit
    Dialog.PasskeyInProgress -> {
      Dialogs.IndeterminateProgressDialog()
    }
    Dialog.PasskeyFailed -> {
      Dialogs.SimpleMessageDialog(
        message = stringResource(R.string.TwoFactorVerifyScreen__something_went_wrong_with_your_passkey),
        dismiss = stringResource(android.R.string.ok),
        onDismiss = { onEvent(TwoFactorVerifyEvent.DialogDismissed) },
        modifier = Modifier.testTag(TwoFactorVerifyTestTags.DIALOG_PASSKEY_FAILED)
      )
    }
    Dialog.NetworkFailure -> {
      Dialogs.SimpleMessageDialog(
        message = stringResource(R.string.TwoFactorVerifyScreen__couldnt_reach_signal),
        dismiss = stringResource(android.R.string.ok),
        onDismiss = { onEvent(TwoFactorVerifyEvent.DialogDismissed) },
        modifier = Modifier.testTag(TwoFactorVerifyTestTags.DIALOG_NETWORK_FAILURE)
      )
    }
  }
}

@Composable
private fun Loading(modifier: Modifier = Modifier) {
  Box(
    contentAlignment = Alignment.Center,
    modifier = modifier
      .fillMaxSize()
      .testTag(TwoFactorVerifyTestTags.LOADING)
  ) {
    CircularProgressIndicator()
  }
}

@Composable
private fun ChooseMethod(
  step: Step.ChooseMethod,
  onEvent: (TwoFactorVerifyEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .appSettingsScreen()
      .padding(horizontal = 24.dp)
  ) {
    Spacer(modifier = Modifier.height(40.dp))

    Title()

    Text(
      text = stringResource(R.string.TwoFactorVerifyScreen__choose_a_method_below_to_verify_your_account),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(top = 16.dp)
    )

    Spacer(modifier = Modifier.height(40.dp))

    MethodCard(
      imageVector = SignalIcons.PersonKeyLight.imageVector,
      title = stringResource(R.string.TwoFactorVerifyScreen__passkey),
      subtitle = stringResource(R.string.TwoFactorVerifyScreen__tap_to_use_your_device_biometrics_or_fido2_passkey),
      onClick = { onEvent(TwoFactorVerifyEvent.PasskeySelected) },
      modifier = Modifier.testTag(TwoFactorVerifyTestTags.PASSKEY_OPTION)
    )

    if (step.hasAuthenticatorApp) {
      Spacer(modifier = Modifier.height(12.dp))

      MethodCard(
        imageVector = SignalIcons.DevicePhone.imageVector,
        title = stringResource(R.string.TwoFactorVerifyScreen__authenticator_app),
        subtitle = stringResource(R.string.TwoFactorVerifyScreen__enter_a_one_time_code_generated_by_your_authenticator_app),
        onClick = { onEvent(TwoFactorVerifyEvent.AuthenticatorAppSelected) },
        modifier = Modifier.testTag(TwoFactorVerifyTestTags.AUTHENTICATOR_APP_OPTION)
      )
    }

    Spacer(modifier = Modifier.height(24.dp))
  }
}

@Composable
private fun MethodCard(
  imageVector: ImageVector,
  title: String,
  subtitle: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Card(
    onClick = onClick,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    modifier = modifier.fillMaxWidth()
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(16.dp)
    ) {
      Icon(
        imageVector = imageVector,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(48.dp)
      )

      Spacer(modifier = Modifier.width(16.dp))

      Column {
        Text(
          text = title,
          style = MaterialTheme.typography.bodyLarge
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

@Composable
private fun EnterCode(
  state: TwoFactorVerifyState,
  onEvent: (TwoFactorVerifyEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .appSettingsScreen()
      .padding(horizontal = 24.dp)
  ) {
    Spacer(modifier = Modifier.height(40.dp))

    Image(
      painter = painterResource(R.drawable.image_totp_phone),
      contentDescription = null,
      modifier = Modifier.align(Alignment.CenterHorizontally)
    )

    Spacer(modifier = Modifier.height(32.dp))

    Title()

    Text(
      text = stringResource(R.string.TwoFactorVerifyScreen__to_continue_enter_the_6_digit_code_from_your_authenticator_app),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(top = 16.dp)
    )

    Spacer(modifier = Modifier.height(32.dp))

    val errorMessage = state.codeError.message()

    CodeEntryField(
      state = state.codeEntry,
      onEvent = { onEvent(TwoFactorVerifyEvent.CodeEntryEvent(it)) },
      enabled = !state.submitting,
      isError = errorMessage != null
    )

    if (errorMessage != null) {
      Text(
        text = errorMessage,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 8.dp)
          .testTag(TwoFactorVerifyTestTags.CODE_ERROR)
      )
    }

    Spacer(modifier = Modifier.height(24.dp))
  }
}

@Composable
private fun Title() {
  Text(
    text = stringResource(R.string.TwoFactorVerifyScreen__two_factor_authentication),
    style = MaterialTheme.typography.headlineMedium,
    modifier = Modifier.fillMaxWidth()
  )
}

/**
 * The message shown under the code field, or null when there's nothing wrong.
 */
@Composable
private fun CodeError.message(): String? = when (this) {
  CodeError.None -> null
  CodeError.IncorrectCode -> stringResource(R.string.TwoFactorVerifyScreen__incorrect_code)
  CodeError.NetworkFailure -> stringResource(R.string.TwoFactorVerifyScreen__couldnt_reach_signal)
}

@DayNightPreviews
@Composable
private fun TwoFactorVerifyScreenLoadingPreview() {
  Previews.Preview {
    TwoFactorVerifyScreen(
      state = TwoFactorVerifyState(),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun TwoFactorVerifyScreenChooseMethodPreview() {
  Previews.Preview {
    TwoFactorVerifyScreen(
      state = TwoFactorVerifyState(step = Step.ChooseMethod(hasAuthenticatorApp = true)),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun TwoFactorVerifyScreenPasskeyOnlyPreview() {
  Previews.Preview {
    TwoFactorVerifyScreen(
      state = TwoFactorVerifyState(step = Step.ChooseMethod(hasAuthenticatorApp = false)),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun TwoFactorVerifyScreenEnterCodePreview() {
  Previews.Preview {
    TwoFactorVerifyScreen(
      state = TwoFactorVerifyState(
        step = Step.EnterCode(canReturnToMethodChoice = false),
        codeEntry = CodeEntryFieldState(code = "418")
      ),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun TwoFactorVerifyScreenIncorrectCodePreview() {
  Previews.Preview {
    TwoFactorVerifyScreen(
      state = TwoFactorVerifyState(
        step = Step.EnterCode(canReturnToMethodChoice = true),
        codeEntry = CodeEntryFieldState(code = "418372"),
        codeError = CodeError.IncorrectCode
      ),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun TwoFactorVerifyScreenPasskeyFailedPreview() {
  Previews.Preview {
    TwoFactorVerifyScreen(
      state = TwoFactorVerifyState(
        step = Step.ChooseMethod(hasAuthenticatorApp = true),
        dialog = Dialog.PasskeyFailed
      ),
      onEvent = {}
    )
  }
}
