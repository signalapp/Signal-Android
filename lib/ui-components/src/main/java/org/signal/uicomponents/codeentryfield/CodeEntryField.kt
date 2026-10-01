/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.uicomponents.codeentryfield

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.uicomponents.codeentryfield.CodeEntryFieldState.Companion.CODE_LENGTH

private val DIGIT_WIDTH = 48.dp
private val DIGIT_HEIGHT = 60.dp
private val DIGIT_SPACING = 8.dp
private val SEPARATOR_PADDING = 18.dp
private val DIGIT_SHAPE = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
private val HIDDEN_SELECTION_COLORS = TextSelectionColors(handleColor = Color.Transparent, backgroundColor = Color.Transparent)

@VisibleForTesting
object CodeEntryFieldTestTags {
  /** The text field itself, for entering text in tests. */
  const val ROOT = "code-entry-field"

  /** The box showing the digit at [index]. Only found in the unmerged semantics tree. */
  fun digit(index: Int): String = "code-entry-field-digit-$index"
}

/**
 * A [CODE_LENGTH]-digit code input, laid out as one box per digit in XXX-XXX format.
 *
 * Under the hood, this is a single invisible text field. It captures all the taps and keystrokes and whatnot, and then
 * we split the submitted code and render it into the different boxes manually. If you try to do 6 distinct text boxes,
 * you tend to run into weird latency issues as focus jumps between the boxes.
 *
 * Driven by a [CodeEntryFieldPresenter], which owns the state handed in here and decides what the events sent back out
 * of here actually do. [modifier] is applied to the text field, so things like autofill content types can be set on it.
 */
@Composable
fun CodeEntryField(
  state: CodeEntryFieldState,
  onEvent: (CodeEntryFieldEvents) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  isError: Boolean = false,
  digitSpacing: Dp = DIGIT_SPACING,
  separatorPadding: Dp = SEPARATOR_PADDING
) {
  val textFieldState = rememberTextFieldState(initialText = state.code)
  val focusRequester = remember { FocusRequester() }
  val currentOnEvent by rememberUpdatedState(onEvent)
  var isFocused by remember { mutableStateOf(false) }

  LaunchedEffect(textFieldState) {
    snapshotFlow { textFieldState.text.toString() }
      .collect { currentOnEvent(CodeEntryFieldEvents.CodeChanged(it)) }
  }

  LaunchedEffect(textFieldState) {
    snapshotFlow { textFieldState.selection }
      .collect { selection ->
        if (selection != TextRange(textFieldState.text.length)) {
          textFieldState.edit { placeCursorAtEnd() }
        }
      }
  }

  LaunchedEffect(state.pendingOverwrite) {
    val code = state.pendingOverwrite ?: return@LaunchedEffect
    textFieldState.setTextAndPlaceCursorAtEnd(code)
    currentOnEvent(CodeEntryFieldEvents.OverwriteApplied)
  }

  LaunchedEffect(enabled) {
    if (enabled) {
      focusRequester.requestFocus()
    }
  }

  CompositionLocalProvider(LocalTextSelectionColors provides HIDDEN_SELECTION_COLORS) {
    BasicTextField(
      state = textFieldState,
      enabled = enabled,
      inputTransformation = CodeInputTransformation,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
      lineLimits = TextFieldLineLimits.SingleLine,
      textStyle = TextStyle(color = Color.Transparent),
      cursorBrush = SolidColor(Color.Transparent),
      modifier = modifier
        .fillMaxWidth()
        .focusRequester(focusRequester)
        .onFocusChanged { isFocused = it.isFocused }
        .testTag(CodeEntryFieldTestTags.ROOT),
      decorator = { innerTextField ->
        Box(contentAlignment = Alignment.Center) {
          DigitBoxes(
            code = textFieldState.text,
            activeIndex = if (isFocused) textFieldState.text.length.coerceAtMost(CODE_LENGTH - 1) else null,
            enabled = enabled,
            isError = isError,
            digitSpacing = digitSpacing,
            separatorPadding = separatorPadding
          )
          Box(modifier = Modifier.matchParentSize()) {
            innerTextField()
          }
        }
      }
    )
  }
}

@Composable
private fun DigitBoxes(
  code: CharSequence,
  activeIndex: Int?,
  enabled: Boolean,
  isError: Boolean,
  digitSpacing: Dp,
  separatorPadding: Dp
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically
  ) {
    for (index in 0 until CODE_LENGTH) {
      if (index == CODE_LENGTH / 2) {
        Text(
          text = "-",
          style = MaterialTheme.typography.headlineMedium,
          color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
          modifier = Modifier.padding(horizontal = separatorPadding)
        )
      } else if (index > 0) {
        Spacer(modifier = Modifier.width(digitSpacing))
      }

      DigitBox(
        digit = code.getOrNull(index)?.toString() ?: "",
        isActive = index == activeIndex,
        enabled = enabled,
        isError = isError,
        testTag = CodeEntryFieldTestTags.digit(index),
        modifier = Modifier.weight(1f, fill = false)
      )
    }
  }
}

@Composable
private fun DigitBox(
  digit: String,
  isActive: Boolean,
  enabled: Boolean,
  isError: Boolean,
  testTag: String,
  modifier: Modifier = Modifier
) {
  val indicatorColor = when {
    !enabled -> MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
    isError -> MaterialTheme.colorScheme.error
    isActive -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.outline
  }
  val indicatorThickness = if (isActive) 2.dp else 1.dp

  Box(
    contentAlignment = Alignment.Center,
    modifier = modifier
      .width(DIGIT_WIDTH)
      .height(DIGIT_HEIGHT)
      .background(color = MaterialTheme.colorScheme.surfaceVariant, shape = DIGIT_SHAPE)
      .drawBehind {
        val thickness = indicatorThickness.toPx()
        val y = size.height - thickness / 2
        drawLine(
          color = indicatorColor,
          start = Offset(0f, y),
          end = Offset(size.width, y),
          strokeWidth = thickness
        )
      }
  ) {
    Text(
      text = digit,
      style = MaterialTheme.typography.titleLarge,
      textAlign = TextAlign.Center,
      color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
      modifier = Modifier
        .fillMaxWidth()
        .testTag(testTag)
        .semantics { hideFromAccessibility() }
    )
  }
}

/**
 * Keeps the field to at most [CODE_LENGTH] digits, dropping anything else (like the dash in a pasted "123-456").
 * Pasting a full code replaces whatever was already entered, and any other input that would overflow the field is
 * rejected.
 */
private object CodeInputTransformation : InputTransformation {
  override fun TextFieldBuffer.transformInput() {
    val digits = asCharSequence().filter { it.isDigit() }.toString()
    val original = originalText.toString()
    val insertedLength = digits.length - original.length

    val sanitized = when {
      digits.length <= CODE_LENGTH -> digits
      digits.startsWith(original) && insertedLength >= CODE_LENGTH -> digits.substring(original.length, original.length + CODE_LENGTH)
      else -> null
    }

    if (sanitized == null) {
      revertAllChanges()
      return
    }

    if (sanitized != asCharSequence().toString()) {
      replace(0, length, sanitized)
    }
    placeCursorAtEnd()
  }
}

@DayNightPreviews
@Composable
private fun CodeEntryFieldPreview() {
  Previews.Preview {
    CodeEntryField(
      state = CodeEntryFieldState(code = "418372"),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun CodeEntryFieldPartiallyFilledPreview() {
  Previews.Preview {
    CodeEntryField(
      state = CodeEntryFieldState(code = "418"),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun CodeEntryFieldErrorPreview() {
  Previews.Preview {
    CodeEntryField(
      state = CodeEntryFieldState(code = "418372"),
      onEvent = {},
      isError = true
    )
  }
}

@DayNightPreviews
@Composable
private fun CodeEntryFieldDisabledPreview() {
  Previews.Preview {
    CodeEntryField(
      state = CodeEntryFieldState(code = "418372"),
      onEvent = {},
      enabled = false
    )
  }
}
