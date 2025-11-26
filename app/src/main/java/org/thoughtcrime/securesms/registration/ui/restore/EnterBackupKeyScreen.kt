/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.restore

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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.horizontalGutters
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.v2.ui.BackupsIconColors
import org.thoughtcrime.securesms.fonts.MonoTypeface
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationScreen
import org.whispersystems.signalservice.api.AccountEntropyPool

/**
 * Shared screen infrastructure for entering an [AccountEntropyPool].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnterBackupKeyScreen(
  isDisplayedDuringManualRestore: Boolean,
  backupKey: String,
  inProgress: Boolean,
  isBackupKeyValid: Boolean,
  chunkLength: Int,
  aepValidationError: AccountEntropyPoolVerification.AEPValidationError?,
  onBackupKeyChanged: (String) -> Unit = {},
  onNextClicked: () -> Unit = {},
  onLearnMore: () -> Unit = {},
  onSkip: () -> Unit = {},
  dialogContent: @Composable () -> Unit
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
          enabled = !inProgress,
          modifier = Modifier.weight(weight = 1f, fill = false),
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

        Spacer(modifier = Modifier.size(24.dp))

        AnimatedContent(
          targetState = inProgress,
          label = "next-progress"
        ) { inProgress ->
          if (inProgress) {
            CircularProgressIndicator(
              modifier = Modifier.size(40.dp)
            )
          } else {
            Buttons.LargeTonal(
              enabled = isBackupKeyValid && aepValidationError == null,
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
    var requestFocus: Boolean by remember { mutableStateOf(true) }
    val visualTransform = remember(chunkLength) { BackupKeyVisualTransformation(chunkSize = chunkLength) }
    val keyboardController = LocalSoftwareKeyboardController.current

    val autoFillHelper = backupKeyAutoFillHelper { onBackupKeyChanged(it) }

    TextField(
      value = backupKey,
      onValueChange = {
        onBackupKeyChanged(it)
        autoFillHelper.onValueChanged(it)
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
        imeAction = ImeAction.Next,
        autoCorrectEnabled = false
      ),
      keyboardActions = KeyboardActions(
        onNext = {
          if (isBackupKeyValid) {
            keyboardController?.hide()
            onNextClicked()
          }
        }
      ),
      supportingText = { aepValidationError?.ValidationErrorMessage() },
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
          onSkip = onSkip,
          showSecondParagraph = isDisplayedDuringManualRestore
        )
      }
    }

    dialogContent()
  }
}

@Composable
private fun AccountEntropyPoolVerification.AEPValidationError.ValidationErrorMessage() {
  when (this) {
    is AccountEntropyPoolVerification.AEPValidationError.TooLong -> Text(text = stringResource(R.string.EnterBackupKey_too_long_error, this.count, this.max))
    AccountEntropyPoolVerification.AEPValidationError.Invalid -> Text(text = stringResource(R.string.EnterBackupKey_invalid_backup_key_error))
    AccountEntropyPoolVerification.AEPValidationError.Incorrect -> Text(text = stringResource(R.string.EnterBackupKey_incorrect_backup_key_error))
  }
}

@DayNightPreviews
@Composable
private fun EnterBackupKeyScreenPreview() {
  Previews.Preview {
    EnterBackupKeyScreen(
      backupKey = "UY38jh2778hjjhj8lk19ga61s672jsj089r023s6a57809bap92j2yh5t326vv7t".uppercase(),
      isBackupKeyValid = true,
      inProgress = false,
      chunkLength = 4,
      aepValidationError = null,
      isDisplayedDuringManualRestore = true
    ) {}
  }
}

@DayNightPreviews
@Composable
private fun EnterBackupKeyScreenErrorPreview() {
  Previews.Preview {
    EnterBackupKeyScreen(
      backupKey = "UY38jh2778hjjhj8lk19ga61s672jsj089r023s6a57809bap92j2yh5t326vv7t".uppercase(),
      isBackupKeyValid = true,
      inProgress = false,
      chunkLength = 4,
      aepValidationError = AccountEntropyPoolVerification.AEPValidationError.Invalid,
      isDisplayedDuringManualRestore = true
    ) {}
  }
}

@Composable
private fun NoBackupKeyBottomSheet(
  showSecondParagraph: Boolean,
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

    if (showSecondParagraph) {
      Spacer(modifier = Modifier.height(24.dp))

      Text(
        text = stringResource(R.string.EnterBackupKey_no_key_paragraph_2),
        style = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center),
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }

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

@DayNightPreviews
@Composable
private fun NoBackupKeyBottomSheetPreview() {
  Previews.BottomSheetPreview {
    NoBackupKeyBottomSheet(
      showSecondParagraph = true
    )
  }
}

@DayNightPreviews
@Composable
private fun NoBackupKeyBottomSheetNoSecondParagraphPreview() {
  Previews.BottomSheetPreview {
    NoBackupKeyBottomSheet(
      showSecondParagraph = false
    )
  }
}
