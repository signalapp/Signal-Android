/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registrationv3.ui.restore

import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.signal.core.ui.BottomSheets
import org.signal.core.ui.Buttons
import org.signal.core.ui.Dialogs
import org.signal.core.ui.Previews
import org.signal.core.ui.SignalPreview
import org.signal.core.ui.horizontalGutters
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.v2.ui.BackupsIconColors
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.registration.data.network.RegisterAccountResult
import org.thoughtcrime.securesms.registrationv3.ui.RegistrationState
import org.thoughtcrime.securesms.registrationv3.ui.RegistrationViewModel
import org.thoughtcrime.securesms.registrationv3.ui.phonenumber.EnterPhoneNumberMode
import org.thoughtcrime.securesms.registrationv3.ui.restore.EnterBackupKeyViewModel.EnterBackupKeyState
import org.thoughtcrime.securesms.registrationv3.ui.shared.RegistrationScreen
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Enter backup key screen for manual Signal Backups restore flow.
 */
class EnterBackupKeyFragment : ComposeFragment() {

  companion object {
    private const val LEARN_MORE_URL = "https://support.signal.org/hc/articles/360007059752"
  }

  private val sharedViewModel by activityViewModels<RegistrationViewModel>()
  private val viewModel by viewModels<EnterBackupKeyViewModel>()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
        sharedViewModel
          .state
          .map { it.registerAccountError }
          .filterNotNull()
          .collect {
            sharedViewModel.registerAccountErrorShown()
            viewModel.handleRegistrationFailure(it)
          }
      }
    }
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sharedState by sharedViewModel.state.collectAsStateWithLifecycle()

    EnterBackupKeyScreen(
      backupKey = viewModel.backupKey,
      state = state,
      sharedState = sharedState,
      onBackupKeyChanged = viewModel::updateBackupKey,
      onNextClicked = {
        viewModel.registering()
        sharedViewModel.registerWithBackupKey(
          context = requireContext(),
          backupKey = viewModel.backupKey,
          e164 = null,
          pin = null
        )
      },
      onRegistrationErrorDismiss = viewModel::clearRegistrationError,
      onBackupKeyHelp = { CommunicationActions.openBrowserLink(requireContext(), LEARN_MORE_URL) },
      onLearnMore = { CommunicationActions.openBrowserLink(requireContext(), LEARN_MORE_URL) },
      onSkip = { findNavController().safeNavigate(EnterBackupKeyFragmentDirections.goToEnterPhoneNumber(EnterPhoneNumberMode.RESTART_AFTER_COLLECTION)) }
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnterBackupKeyScreen(
  backupKey: String,
  state: EnterBackupKeyState,
  sharedState: RegistrationState,
  onBackupKeyChanged: (String) -> Unit = {},
  onRegistrationErrorDismiss: () -> Unit = {},
  onBackupKeyHelp: () -> Unit = {},
  onNextClicked: () -> Unit = {},
  onLearnMore: () -> Unit = {},
  onSkip: () -> Unit = {}
) {
  val coroutineScope = rememberCoroutineScope()
  val sheetState = rememberModalBottomSheetState(
    skipPartiallyExpanded = true
  )

  RegistrationScreen(
    title = stringResource(R.string.EnterBackupKey_title),
    subtitle = stringResource(R.string.EnterBackupKey_subtitle),
    bottomContent = {
      Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
      ) {
        TextButton(
          enabled = !sharedState.inProgress,
          onClick = {
            coroutineScope.launch {
              sheetState.show()
            }
          }
        ) {
          Text(
            text = stringResource(id = R.string.EnterBackupKey_no_backup_key)
          )
        }

        AnimatedContent(
          targetState = state.isRegistering,
          label = "next-progress"
        ) { isRegistering ->
          if (isRegistering) {
            CircularProgressIndicator(
              modifier = Modifier.size(40.dp)
            )
          } else {
            Buttons.LargeTonal(
              enabled = state.backupKeyValid && state.aepValidationError == null,
              onClick = onNextClicked
            ) {
              Text(
                text = stringResource(id = R.string.RegistrationActivity_next)
              )
            }
          }
        }
      }
    }
  ) {
    val focusRequester = remember { FocusRequester() }
    val visualTransform = remember(state.chunkLength) { BackupKeyVisualTransformation(chunkSize = state.chunkLength) }
    val keyboardController = LocalSoftwareKeyboardController.current

    TextField(
      value = backupKey,
      onValueChange = onBackupKeyChanged,
      label = {
        Text(text = stringResource(id = R.string.EnterBackupKey_backup_key))
      },
      textStyle = LocalTextStyle.current.copy(
        fontFamily = FontFamily(typeface = Typeface.MONOSPACE),
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
          if (state.backupKeyValid) {
            keyboardController?.hide()
            onNextClicked()
          }
        }
      ),
      supportingText = { state.aepValidationError?.ValidationErrorMessage() },
      isError = state.aepValidationError != null,
      minLines = 4,
      visualTransformation = visualTransform,
      modifier = Modifier
        .fillMaxWidth()
        .focusRequester(focusRequester)
    )

    LaunchedEffect(Unit) {
      focusRequester.requestFocus()
    }

    if (sheetState.isVisible) {
      ModalBottomSheet(
        dragHandle = null,
        onDismissRequest = {
          coroutineScope.launch {
            sheetState.hide()
          }
        }
      ) {
        NoBackupKeyBottomSheet(
          onLearnMore = {
            coroutineScope.launch {
              sheetState.hide()
            }
            onLearnMore()
          },
          onSkip = onSkip
        )
      }
    }

    if (state.showRegistrationError) {
      if (state.registerAccountResult is RegisterAccountResult.IncorrectRecoveryPassword) {
        Dialogs.SimpleAlertDialog(
          title = stringResource(R.string.EnterBackupKey_incorrect_backup_key_title),
          body = stringResource(R.string.EnterBackupKey_incorrect_backup_key_message),
          confirm = stringResource(R.string.EnterBackupKey_try_again),
          dismiss = stringResource(R.string.EnterBackupKey_backup_key_help),
          onConfirm = {},
          onDeny = onBackupKeyHelp,
          onDismiss = onRegistrationErrorDismiss
        )
      } else {
        val message = when (state.registerAccountResult) {
          is RegisterAccountResult.RateLimited -> stringResource(R.string.RegistrationActivity_you_have_made_too_many_attempts_please_try_again_later)
          else -> stringResource(R.string.RegistrationActivity_error_connecting_to_service)
        }

        Dialogs.SimpleMessageDialog(
          message = message,
          onDismiss = onRegistrationErrorDismiss,
          dismiss = stringResource(android.R.string.ok)
        )
      }
    }
  }
}

@Composable
private fun EnterBackupKeyViewModel.AEPValidationError.ValidationErrorMessage() {
  when (this) {
    is EnterBackupKeyViewModel.AEPValidationError.TooLong -> Text(text = stringResource(R.string.EnterBackupKey_too_long_error, this.count, this.max))
    EnterBackupKeyViewModel.AEPValidationError.Invalid -> Text(text = stringResource(R.string.EnterBackupKey_invalid_backup_key_error))
    EnterBackupKeyViewModel.AEPValidationError.Incorrect -> Text(text = stringResource(R.string.EnterBackupKey_incorrect_backup_key_error))
  }
}

@SignalPreview
@Composable
private fun EnterBackupKeyScreenPreview() {
  Previews.Preview {
    EnterBackupKeyScreen(
      backupKey = "UY38jh2778hjjhj8lk19ga61s672jsj089r023s6a57809bap92j2yh5t326vv7t",
      state = EnterBackupKeyState(requiredLength = 64, chunkLength = 4),
      sharedState = RegistrationState(phoneNumber = null, recoveryPassword = null)
    )
  }
}

@SignalPreview
@Composable
private fun EnterBackupKeyScreenErrorPreview() {
  Previews.Preview {
    EnterBackupKeyScreen(
      backupKey = "UY38jh2778hjjhj8lk19ga61s672jsj089r023s6a57809bap92j2yh5t326vv7t",
      state = EnterBackupKeyState(requiredLength = 64, chunkLength = 4, aepValidationError = EnterBackupKeyViewModel.AEPValidationError.Invalid),
      sharedState = RegistrationState(phoneNumber = null, recoveryPassword = null)
    )
  }
}

@Composable
private fun NoBackupKeyBottomSheet(
  onLearnMore: () -> Unit = {},
  onSkip: () -> Unit = {}
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .fillMaxWidth()
      .horizontalGutters()
  ) {
    BottomSheets.Handle()

    Icon(
      painter = painterResource(id = R.drawable.symbol_key_24),
      tint = BackupsIconColors.Success.foreground,
      contentDescription = null,
      modifier = Modifier
        .padding(top = 18.dp, bottom = 16.dp)
        .size(88.dp)
        .background(
          color = BackupsIconColors.Success.background,
          shape = CircleShape
        )
        .padding(20.dp)
    )

    Text(
      text = stringResource(R.string.EnterBackupKey_no_backup_key),
      style = MaterialTheme.typography.titleLarge
    )

    Spacer(modifier = Modifier.height(12.dp))

    Text(
      text = stringResource(R.string.EnterBackupKey_no_key_paragraph_1),
      style = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center),
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(24.dp))

    Text(
      text = stringResource(R.string.EnterBackupKey_no_key_paragraph_1),
      style = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center),
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(36.dp))

    Row(
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 24.dp)
    ) {
      TextButton(
        onClick = onLearnMore
      ) {
        Text(
          text = stringResource(id = R.string.EnterBackupKey_learn_more)
        )
      }

      TextButton(
        onClick = onSkip
      ) {
        Text(
          text = stringResource(id = R.string.EnterBackupKey_skip_and_dont_restore)
        )
      }
    }
  }
}

@SignalPreview
@Composable
private fun NoBackupKeyBottomSheetPreview() {
  Previews.BottomSheetPreview {
    NoBackupKeyBottomSheet()
  }
}
