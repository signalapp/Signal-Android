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
import androidx.compose.runtime.saveable.rememberSaveable
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

private const val PASSPHRASE_LENGTH = 30
private const val CHUNK_SIZE = 5

@Composable
fun EnterLocalBackupV1PassphaseScreen(
  onSubmit: (String) -> Unit,
  onCancel: () -> Unit,
  modifier: Modifier = Modifier
) {
  var passphrase by rememberSaveable { mutableStateOf("") }
  val scrollState = rememberScrollState()
  val visualTransform = remember { PassphraseVisualTransformation(CHUNK_SIZE) }
  val focusRequester = remember { FocusRequester() }
  var requestFocus by remember { mutableStateOf(true) }
  val keyboardController = LocalSoftwareKeyboardController.current
  val isValid = passphrase.length == PASSPHRASE_LENGTH
  val isTooLong = passphrase.length > PASSPHRASE_LENGTH

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(scrollState)
      .padding(horizontal = 24.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Spacer(modifier = Modifier.height(40.dp))

    Text(
      text = stringResource(R.string.LocalBackupRestoreScreen__enter_backup_passphrase),
      style = MaterialTheme.typography.headlineMedium,
      modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
      text = stringResource(R.string.LocalBackupRestoreScreen__enter_the_30_digit_passphrase),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(24.dp))

    TextField(
      value = passphrase,
      onValueChange = { newValue ->
        passphrase = newValue.filter { it.isDigit() }
      },
      label = { Text(stringResource(R.string.LocalBackupRestoreScreen__recovery_key)) },
      textStyle = MaterialTheme.typography.bodyLarge.copy(
        fontFamily = FontFamily.Monospace,
        lineHeight = 36.sp
      ),
      keyboardOptions = KeyboardOptions(
        keyboardType = KeyboardType.Number,
        imeAction = ImeAction.Next,
        autoCorrectEnabled = false
      ),
      keyboardActions = KeyboardActions(
        onNext = {
          if (isValid) {
            keyboardController?.hide()
            onSubmit(passphrase)
          }
        }
      ),
      supportingText = {
        if (isTooLong) {
          Text(stringResource(R.string.LocalBackupRestoreScreen__too_long, passphrase.length, PASSPHRASE_LENGTH))
        }
      },
      isError = isTooLong,
      minLines = 2,
      visualTransformation = visualTransform,
      modifier = Modifier
        .fillMaxWidth()
        .focusRequester(focusRequester)
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
        onClick = onCancel
      ) {
        Text(text = stringResource(R.string.LocalBackupRestoreScreen__no_passphrase))
      }

      Spacer(modifier = Modifier.size(24.dp))

      Buttons.LargeTonal(
        enabled = isValid,
        onClick = { onSubmit(passphrase) }
      ) {
        Text(text = stringResource(R.string.LocalBackupRestoreScreen__next))
      }
    }

    Spacer(modifier = Modifier.height(32.dp))
  }
}

/**
 * Visual formatter for passphrases — groups digits with spaces.
 */
private class PassphraseVisualTransformation(private val chunkSize: Int) : VisualTransformation {
  override fun filter(text: AnnotatedString): TransformedText {
    var output = ""
    for ((i, c) in text.withIndex()) {
      output += c
      if (i % chunkSize == chunkSize - 1) {
        output += " "
      }
    }

    val transformed = output.trimEnd()

    return TransformedText(
      text = AnnotatedString(transformed),
      offsetMapping = PassphraseOffsetMapping(chunkSize, text.length)
    )
  }

  private class PassphraseOffsetMapping(private val chunkSize: Int, private val inputSize: Int) : OffsetMapping {
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
private fun EnterLocalBackupV1PassphaseScreenPreview() {
  Previews.Preview {
    EnterLocalBackupV1PassphaseScreen(
      onSubmit = {},
      onCancel = {}
    )
  }
}

@AllDevicePreviews
@Composable
private fun EnterLocalBackupV1PassphaseScreenFilledPreview() {
  Previews.Preview {
    val visualTransform = remember { PassphraseVisualTransformation(CHUNK_SIZE) }

    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Spacer(modifier = Modifier.height(40.dp))

      Text(
        text = stringResource(R.string.LocalBackupRestoreScreen__enter_backup_passphrase),
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.fillMaxWidth()
      )

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = stringResource(R.string.LocalBackupRestoreScreen__enter_the_30_digit_passphrase),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth()
      )

      Spacer(modifier = Modifier.height(24.dp))

      TextField(
        value = "814680481455087435556426352670",
        onValueChange = {},
        label = { Text(stringResource(R.string.LocalBackupRestoreScreen__recovery_key)) },
        textStyle = MaterialTheme.typography.bodyLarge.copy(
          fontFamily = FontFamily.Monospace,
          lineHeight = 36.sp
        ),
        minLines = 2,
        visualTransformation = visualTransform,
        modifier = Modifier.fillMaxWidth()
      )

      Spacer(modifier = Modifier.weight(1f))

      Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
      ) {
        TextButton(
          modifier = Modifier.weight(weight = 1f, fill = false),
          onClick = {}
        ) {
          Text(text = stringResource(R.string.LocalBackupRestoreScreen__no_passphrase))
        }

        Spacer(modifier = Modifier.size(24.dp))

        Buttons.LargeTonal(
          enabled = true,
          onClick = {}
        ) {
          Text(text = stringResource(R.string.LocalBackupRestoreScreen__next))
        }
      }

      Spacer(modifier = Modifier.height(32.dp))
    }
  }
}
