/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

object TextFields {

  /**
   * This is intended to replicate what TextField exposes but allows us to set our own content padding as
   * well as resolving the auto-scroll to cursor position issue.
   *
   * Prefer the base TextField where possible.
   */
  @OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
  @Composable
  fun TextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    shape: Shape = TextFieldDefaults.shape,
    colors: TextFieldColors = TextFieldDefaults.colors(),
    contentPadding: PaddingValues =
      if (label == null) {
        TextFieldDefaults.contentPaddingWithoutLabel()
      } else {
        TextFieldDefaults.contentPaddingWithLabel()
      }
  ) {
    // If color is not provided via the text style, use content color as a default
    val textColor = textStyle.color.takeOrElse {
      LocalContentColor.current
    }
    val mergedTextStyle = textStyle.merge(TextStyle(color = textColor))
    val cursorColor = rememberUpdatedState(newValue = if (isError) MaterialTheme.colorScheme.error else textColor)

    // Borrowed from BasicTextField, all this helps reduce recompositions.
    var lastTextValue by remember(value) { mutableStateOf(value) }
    var textFieldValueState by remember {
      mutableStateOf(
        TextFieldValue(
          text = value,
          selection = value.createSelection()
        )
      )
    }

    val textFieldValue = textFieldValueState.copy(
      text = value,
      selection = if (textFieldValueState.text.isBlank()) value.createSelection() else textFieldValueState.selection
    )

    SideEffect {
      if (textFieldValue.selection != textFieldValueState.selection ||
        textFieldValue.composition != textFieldValueState.composition
      ) {
        textFieldValueState = textFieldValue
      }
    }

    var hasFocus by remember { mutableStateOf(false) }

    // BasicTextField has a bug where it won't scroll down to keep the cursor in view.
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()

    CompositionLocalProvider(LocalTextSelectionColors provides TextSelectionColors(handleColor = LocalContentColor.current, LocalContentColor.current.copy(alpha = 0.4f))) {
      BasicTextField(
        value = textFieldValue,
        modifier = modifier
          .onFocusChanged { }
          .bringIntoViewRequester(bringIntoViewRequester)
          .onFocusChanged { focusState -> hasFocus = focusState.hasFocus }
          .defaultMinSize(
            minWidth = TextFieldDefaults.MinWidth,
            minHeight = TextFieldDefaults.MinHeight
          ),
        onValueChange = { newTextFieldValueState ->
          textFieldValueState = newTextFieldValueState

          val stringChangedSinceLastInvocation = lastTextValue != newTextFieldValueState.text
          lastTextValue = newTextFieldValueState.text

          if (stringChangedSinceLastInvocation) {
            onValueChange(newTextFieldValueState.text)
          }
        },
        enabled = enabled,
        readOnly = readOnly,
        textStyle = mergedTextStyle,
        cursorBrush = SolidColor(cursorColor.value),
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = interactionSource,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = { result ->
          if (hasFocus && textFieldValue.selection.collapsed) {
            val rect = result.getCursorRect(textFieldValue.selection.start)

            coroutineScope.launch {
              bringIntoViewRequester.bringIntoView(rect.translate(translateX = 0f, translateY = 72.dp.value))
            }
          }
        },
        decorationBox = @Composable { innerTextField ->
          // places leading icon, text field with label and placeholder, trailing icon
          TextFieldDefaults.DecorationBox(
            value = value,
            visualTransformation = visualTransformation,
            innerTextField = innerTextField,
            placeholder = placeholder,
            label = label,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            prefix = prefix,
            suffix = suffix,
            supportingText = supportingText,
            shape = shape,
            singleLine = singleLine,
            enabled = enabled,
            isError = isError,
            interactionSource = interactionSource,
            colors = colors,
            contentPadding = contentPadding
          )
        }
      )
    }
  }

  private fun String.createSelection(): TextRange {
    return when {
      isEmpty() -> TextRange.Zero
      else -> TextRange(length, length)
    }
  }
}
