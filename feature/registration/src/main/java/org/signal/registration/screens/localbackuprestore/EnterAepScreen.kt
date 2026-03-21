/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.localbackuprestore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Previews
import org.signal.registration.R

@Composable
fun EnterAepScreen(
  state: EnterAepState,
  onEvent: (EnterAepEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  val scrollState = rememberScrollState()
  val visualTransform = remember(state.chunkLength) { AepVisualTransformation(state.chunkLength) }
  val focusRequester = remember { FocusRequester() }
  var requestFocus by remember { mutableStateOf(true) }
  val keyboardController = LocalSoftwareKeyboardController.current
  val autoFillHelper = backupKeyAutoFillHelper { onEvent(EnterAepEvents.BackupKeyChanged(it)) }

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(scrollState)
      .padding(horizontal = 24.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Spacer(modifier = Modifier.height(40.dp))

    Text(
      text = stringResource(R.string.EnterAepScreen__enter_your_recovery_key),
      style = MaterialTheme.typography.headlineMedium,
      modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
      text = stringResource(R.string.EnterAepScreen__your_recovery_key_is_a_64_character_code),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(24.dp))

    TextField(
      value = state.backupKey,
      onValueChange = {
        onEvent(EnterAepEvents.BackupKeyChanged(it))
        autoFillHelper.onValueChanged(it)
      },
      label = { Text(stringResource(R.string.EnterAepScreen__recovery_key)) },
      textStyle = MaterialTheme.typography.bodyLarge.copy(
        fontFamily = FontFamily.Monospace,
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
          null -> {}
        }
      },
      isError = state.aepValidationError != null,
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

    Spacer(modifier = Modifier.weight(1f))

    Row(
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier.fillMaxWidth()
    ) {
      TextButton(
        modifier = Modifier.weight(weight = 1f, fill = false),
        onClick = { onEvent(EnterAepEvents.Cancel) }
      ) {
        Text(text = stringResource(R.string.EnterAepScreen__no_recovery_key))
      }

      Spacer(modifier = Modifier.size(24.dp))

      Buttons.LargeTonal(
        enabled = state.isBackupKeyValid && state.aepValidationError == null,
        onClick = { onEvent(EnterAepEvents.Submit) }
      ) {
        Text(text = stringResource(R.string.LocalBackupRestoreScreen__next))
      }
    }

    Spacer(modifier = Modifier.height(32.dp))
  }
}

/**
 * Visual formatter for backup keys — groups characters with spaces.
 */
private class AepVisualTransformation(private val chunkSize: Int) : VisualTransformation {
  override fun filter(text: AnnotatedString): TransformedText {
    var output = ""
    for ((i, c) in text.withIndex()) {
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
      state = EnterAepState(),
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
        backupKey = "uy38jh2778hjjhj8lk19ga61s672jsj089r023s6a57809bap92j2yh5t326vv7t",
        isBackupKeyValid = true
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
        backupKey = "uy38jh2778hjjhj8lk19ga61s672jsj089r023s6a57809bap92j2yh5t326vv7t",
        isBackupKeyValid = false,
        aepValidationError = AepValidationError.Invalid
      ),
      onEvent = {}
    )
  }
}
