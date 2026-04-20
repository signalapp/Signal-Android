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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import org.signal.core.ui.WindowBreakpoint
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.rememberWindowBreakpoint
import org.signal.registration.R
import org.signal.registration.screens.RegistrationScreen
import org.signal.registration.screens.localbackuprestore.attachBackupKeyAutoFillHelper
import org.signal.registration.screens.localbackuprestore.backupKeyAutoFillHelper

@Composable
fun EnterAepScreen(
  state: EnterAepState,
  onEvent: (EnterAepEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  val windowBreakpoint = rememberWindowBreakpoint()

  when (windowBreakpoint) {
    WindowBreakpoint.SMALL -> {
      CompactLayout(state, onEvent)
    }

    WindowBreakpoint.MEDIUM -> {
      MediumLayout(state, onEvent)
    }

    WindowBreakpoint.LARGE -> {
      LargeLayout(state, onEvent)
    }
  }
}

@Composable
private fun CompactLayout(state: EnterAepState, onEvent: (EnterAepEvents) -> Unit) {
  val scrollState = rememberScrollState()
  RegistrationScreen(
    modifier = Modifier.fillMaxSize(),
    content = {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(scrollState)
          .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Spacer(modifier = Modifier.height(40.dp))
        Description()
        Spacer(modifier = Modifier.height(24.dp))
        RecoveryKeyTextField(state, onEvent)
      }
    },
    footer = {
      Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
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
  )
}

@Composable
private fun MediumLayout(state: EnterAepState, onEvent: (EnterAepEvents) -> Unit) {
  val scrollState = rememberScrollState()
  RegistrationScreen(
    modifier = Modifier.fillMaxSize(),
    content = {
      Row(
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(scrollState)
      ) {
        Column(
          modifier = Modifier.weight(1f).padding(horizontal = 24.dp)
        ) {
          Spacer(modifier = Modifier.height(40.dp))
          Description()
        }
        Column(
          modifier = Modifier.weight(1f).padding(horizontal = 24.dp)
        ) {
          Spacer(modifier = Modifier.height(40.dp))
          RecoveryKeyTextField(state, onEvent)
        }
      }
    },
    footer = {
      Row(
        horizontalArrangement = Arrangement.End,
        modifier = Modifier.fillMaxWidth().padding(16.dp)
      ) {
        NoRecoverKeyButton(onEvent)
        Spacer(modifier = Modifier.size(24.dp))
        NextButton(state, onEvent)
      }
    }
  )
}

@Composable
private fun LargeLayout(state: EnterAepState, onEvent: (EnterAepEvents) -> Unit) {
  val scrollState = rememberScrollState()
  RegistrationScreen(
    modifier = Modifier.fillMaxSize(),
    content = {
      Row(
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(scrollState)
      ) {
        Column(
          modifier = Modifier.weight(1f).padding(horizontal = 24.dp)
        ) {
          Spacer(modifier = Modifier.height(40.dp))

          Description()
        }
        Column(
          modifier = Modifier.weight(1f).padding(horizontal = 24.dp)
        ) {
          Spacer(modifier = Modifier.height(40.dp))

          RecoveryKeyTextField(state, onEvent)
        }
      }
    },
    footer = {
      Row(
        horizontalArrangement = Arrangement.End,
        modifier = Modifier.fillMaxWidth().padding(16.dp)
      ) {
        NoRecoverKeyButton(onEvent)
        Spacer(modifier = Modifier.size(24.dp))
        NextButton(state, onEvent)
      }
    }
  )
}

@Composable
private fun Description() {
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
}

@Composable
private fun RecoveryKeyTextField(state: EnterAepState, onEvent: (EnterAepEvents) -> Unit) {
  val visualTransform = remember(state.chunkLength) { AepVisualTransformation(state.chunkLength) }
  val focusRequester = remember { FocusRequester() }
  var requestFocus by remember { mutableStateOf(true) }
  val keyboardController = LocalSoftwareKeyboardController.current
  val autoFillHelper = backupKeyAutoFillHelper { onEvent(EnterAepEvents.BackupKeyChanged(it)) }

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
private fun NoRecoverKeyButton(onEvent: (EnterAepEvents) -> Unit, modifier: Modifier = Modifier) {
  TextButton(
    modifier = modifier,
    shape = RoundedCornerShape(0.dp),
    onClick = { onEvent(EnterAepEvents.Cancel) }
  ) {
    Text(text = stringResource(R.string.EnterAepScreen__no_recovery_key))
  }
}

@Composable
private fun NextButton(state: EnterAepState, onEvent: (EnterAepEvents) -> Unit, modifier: Modifier = Modifier) {
  Buttons.LargeTonal(
    modifier = modifier,
    enabled = state.isBackupKeyValid && state.aepValidationError == null && !state.isRegistering,
    onClick = { onEvent(EnterAepEvents.Submit) }
  ) {
    Text(text = stringResource(R.string.LocalBackupRestoreScreen__next))
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
