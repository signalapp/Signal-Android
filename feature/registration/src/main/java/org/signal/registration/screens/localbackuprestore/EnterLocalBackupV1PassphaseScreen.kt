/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.localbackuprestore

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
import org.signal.registration.screens.OnePaneRegistrationScaffold
import org.signal.registration.screens.RegistrationScaffold
import org.signal.registration.screens.TwoPaneRegistrationScaffold
import org.signal.registration.screens.attachDebugLogHelper
import org.signal.registration.test.TestTags

private const val PASSPHRASE_LENGTH = 30
private const val CHUNK_SIZE = 5

@Composable
fun EnterLocalBackupV1PassphaseScreen(
  onSubmit: (String) -> Unit,
  onCancel: () -> Unit,
  modifier: Modifier = Modifier
) {
  var passphrase by rememberSaveable { mutableStateOf("") }
  val isValid = passphrase.length == PASSPHRASE_LENGTH
  val isTooLong = passphrase.length > PASSPHRASE_LENGTH

  when (val layoutParams = RegistrationScaffold.rememberLayoutParams()) {
    is RegistrationScaffold.Params.OnePane -> OnePaneLayout(
      params = layoutParams,
      passphrase = passphrase,
      onPassphraseChange = { passphrase = it },
      isValid = isValid,
      isTooLong = isTooLong,
      onSubmit = onSubmit,
      onCancel = onCancel,
      modifier = modifier
    )

    is RegistrationScaffold.Params.TwoPane -> TwoPaneLayout(
      params = layoutParams,
      passphrase = passphrase,
      onPassphraseChange = { passphrase = it },
      isValid = isValid,
      isTooLong = isTooLong,
      onSubmit = onSubmit,
      onCancel = onCancel,
      modifier = modifier
    )
  }
}

@Composable
private fun OnePaneLayout(
  params: RegistrationScaffold.Params.OnePane,
  passphrase: String,
  onPassphraseChange: (String) -> Unit,
  isValid: Boolean,
  isTooLong: Boolean,
  onSubmit: (String) -> Unit,
  onCancel: () -> Unit,
  modifier: Modifier
) {
  val scrollState = rememberScrollState()
  OnePaneRegistrationScaffold(
    modifier = modifier
      .fillMaxSize()
      .testTag(TestTags.ENTER_LOCAL_BACKUP_PASSPHRASE_SCREEN),
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
        PassphraseTextField(
          passphrase = passphrase,
          onPassphraseChange = onPassphraseChange,
          isValid = isValid,
          isTooLong = isTooLong,
          onSubmit = onSubmit
        )
      }
    },
    footer = {
      FooterButtons(
        isValid = isValid,
        passphrase = passphrase,
        isElevated = scrollState.canScrollForward,
        onSubmit = onSubmit,
        onCancel = onCancel
      )
    }
  )
}

@Composable
private fun TwoPaneLayout(
  params: RegistrationScaffold.Params.TwoPane,
  passphrase: String,
  onPassphraseChange: (String) -> Unit,
  isValid: Boolean,
  isTooLong: Boolean,
  onSubmit: (String) -> Unit,
  onCancel: () -> Unit,
  modifier: Modifier
) {
  val firstPaneScrollState = rememberScrollState()
  val secondPaneScrollState = rememberScrollState()

  TwoPaneRegistrationScaffold(
    modifier = modifier
      .fillMaxSize()
      .testTag(TestTags.ENTER_LOCAL_BACKUP_PASSPHRASE_SCREEN),
    params = params,
    firstPane = { paddingValues ->
      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
          .verticalScroll(firstPaneScrollState)
          .padding(paddingValues)
      ) {
        Description(twoPane = true)
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
        PassphraseTextField(
          passphrase = passphrase,
          onPassphraseChange = onPassphraseChange,
          isValid = isValid,
          isTooLong = isTooLong,
          onSubmit = onSubmit
        )
      }
    },
    footer = {
      FooterButtons(
        isValid = isValid,
        passphrase = passphrase,
        isElevated = firstPaneScrollState.canScrollForward || secondPaneScrollState.canScrollForward,
        onSubmit = onSubmit,
        onCancel = onCancel
      )
    }
  )
}

@Composable
private fun Description(twoPane: Boolean = false) {
  Text(
    text = stringResource(R.string.LocalBackupRestoreScreen__enter_backup_passphrase),
    style = if (twoPane) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium,
    modifier = Modifier
      .fillMaxWidth()
      .attachDebugLogHelper()
  )

  Text(
    text = stringResource(R.string.LocalBackupRestoreScreen__enter_the_30_digit_passphrase),
    style = if (twoPane) MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal) else MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(top = 16.dp)
  )
}

@Composable
private fun PassphraseTextField(
  passphrase: String,
  onPassphraseChange: (String) -> Unit,
  isValid: Boolean,
  isTooLong: Boolean,
  onSubmit: (String) -> Unit
) {
  val visualTransform = remember { PassphraseVisualTransformation(CHUNK_SIZE) }
  val focusRequester = remember { FocusRequester() }
  var requestFocus by remember { mutableStateOf(true) }
  val keyboardController = LocalSoftwareKeyboardController.current

  TextField(
    value = passphrase,
    onValueChange = { newValue ->
      onPassphraseChange(newValue.filter { it.isDigit() })
    },
    label = { Text(stringResource(R.string.LocalBackupRestoreScreen__passphrase)) },
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
      .testTag(TestTags.ENTER_LOCAL_BACKUP_PASSPHRASE_INPUT)
      .focusRequester(focusRequester)
      .onGloballyPositioned {
        if (requestFocus) {
          focusRequester.requestFocus()
          requestFocus = false
        }
      }
  )
}

@Composable
private fun FooterButtons(
  isValid: Boolean,
  passphrase: String,
  isElevated: Boolean,
  onSubmit: (String) -> Unit,
  onCancel: () -> Unit
) {
  RegistrationScaffold.FooterSurface(
    isElevated = isElevated
  ) {
    Row(
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
      TextButton(
        modifier = Modifier
          .weight(weight = 1f, fill = false)
          .testTag(TestTags.ENTER_LOCAL_BACKUP_PASSPHRASE_NO_PASSPHRASE_BUTTON),
        onClick = onCancel,
        shape = RoundedCornerShape(0.dp)
      ) {
        Text(text = stringResource(R.string.LocalBackupRestoreScreen__no_passphrase))
      }

      Spacer(modifier = Modifier.size(24.dp))

      Buttons.LargeTonal(
        enabled = isValid,
        onClick = { onSubmit(passphrase) },
        modifier = Modifier.testTag(TestTags.ENTER_LOCAL_BACKUP_PASSPHRASE_NEXT_BUTTON)
      ) {
        Text(text = stringResource(R.string.LocalBackupRestoreScreen__next))
      }
    }
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
    var passphrase by remember { mutableStateOf("814680481455087435556426352670") }

    when (val layoutParams = RegistrationScaffold.rememberLayoutParams()) {
      is RegistrationScaffold.Params.OnePane -> OnePaneLayout(
        params = layoutParams,
        passphrase = passphrase,
        onPassphraseChange = { passphrase = it },
        isValid = true,
        isTooLong = false,
        onSubmit = {},
        onCancel = {},
        modifier = Modifier
      )

      is RegistrationScaffold.Params.TwoPane -> TwoPaneLayout(
        params = layoutParams,
        passphrase = passphrase,
        onPassphraseChange = { passphrase = it },
        isValid = true,
        isTooLong = false,
        onSubmit = {},
        onCancel = {},
        modifier = Modifier
      )
    }
  }
}
