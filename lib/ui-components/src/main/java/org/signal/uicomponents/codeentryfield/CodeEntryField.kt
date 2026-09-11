/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.uicomponents.codeentryfield

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.uicomponents.codeentryfield.CodeEntryFieldState.Companion.CODE_LENGTH

private val DIGIT_WIDTH = 48.dp
private val DIGIT_SPACING = 8.dp
private val SEPARATOR_PADDING = 18.dp
private val DIGIT_SHAPE = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)

@VisibleForTesting
object CodeEntryFieldTestTags {
  const val ROOT = "code-entry-field"

  fun digit(index: Int): String = "code-entry-field-digit-$index"
}

/**
 * A [CODE_LENGTH]-digit code input, laid out as one box per digit in XXX-XXX format. Focus follows along as the user
 * types, and a pasted code fills every box at once.
 *
 * Driven entirely by a [CodeEntryFieldPresenter], which owns the state handed in here and decides what the events sent
 * back out of here actually do.
 */
@Composable
fun CodeEntryField(
  state: CodeEntryFieldState,
  onEvent: (CodeEntryFieldEvents) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  isError: Boolean = false
) {
  val focusRequesters = remember { List(CODE_LENGTH) { FocusRequester() } }

  LaunchedEffect(state.focusedDigitIndex, enabled) {
    if (enabled) {
      focusRequesters[state.focusedDigitIndex.coerceIn(0, CODE_LENGTH - 1)].requestFocus()
    }
  }

  Row(
    modifier = modifier
      .fillMaxWidth()
      .testTag(CodeEntryFieldTestTags.ROOT),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically
  ) {
    for (index in 0 until CODE_LENGTH) {
      if (index == CODE_LENGTH / 2) {
        Text(
          text = "-",
          style = MaterialTheme.typography.headlineMedium,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.padding(horizontal = SEPARATOR_PADDING)
        )
      } else if (index > 0) {
        Spacer(modifier = Modifier.width(DIGIT_SPACING))
      }

      DigitField(
        value = state.digits.getOrElse(index) { "" },
        onValueChange = { onEvent(CodeEntryFieldEvents.DigitChanged(index, it)) },
        focusRequester = focusRequesters[index],
        enabled = enabled,
        isError = isError,
        modifier = Modifier
          .weight(1f, fill = false)
          .testTag(CodeEntryFieldTestTags.digit(index))
      )
    }
  }
}

@Composable
private fun DigitField(
  value: String,
  onValueChange: (String) -> Unit,
  focusRequester: FocusRequester,
  enabled: Boolean,
  isError: Boolean,
  modifier: Modifier = Modifier
) {
  TextField(
    value = value,
    onValueChange = onValueChange,
    modifier = modifier
      .width(DIGIT_WIDTH)
      .focusRequester(focusRequester)
      .onKeyEvent { keyEvent ->
        if ((keyEvent.key == Key.Backspace || keyEvent.key == Key.Delete) && value.isEmpty()) {
          onValueChange("")
          true
        } else {
          false
        }
      },
    textStyle = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center),
    enabled = enabled,
    isError = isError,
    singleLine = true,
    shape = DIGIT_SHAPE,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    colors = TextFieldDefaults.colors(
      focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
      unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
      disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
      errorContainerColor = MaterialTheme.colorScheme.surfaceVariant,
      focusedIndicatorColor = MaterialTheme.colorScheme.primary,
      unfocusedIndicatorColor = MaterialTheme.colorScheme.outline
    )
  )
}

@DayNightPreviews
@Composable
private fun CodeEntryFieldPreview() {
  Previews.Preview {
    CodeEntryField(
      state = CodeEntryFieldState(digits = listOf("4", "1", "8", "3", "7", "2")),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun CodeEntryFieldPartiallyFilledPreview() {
  Previews.Preview {
    CodeEntryField(
      state = CodeEntryFieldState(digits = listOf("4", "1", "8", "", "", ""), focusedDigitIndex = 3),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun CodeEntryFieldErrorPreview() {
  Previews.Preview {
    CodeEntryField(
      state = CodeEntryFieldState(digits = listOf("4", "1", "8", "3", "7", "2")),
      onEvent = {},
      isError = true
    )
  }
}
