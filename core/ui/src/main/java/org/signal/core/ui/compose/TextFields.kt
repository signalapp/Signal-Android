/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.PreviewWrapper
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

  /**
   * The pill-shaped search field used in search app bars, with its leading and trailing [IconButton]s placed to spec.
   */
  @Composable
  fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
  ) {
    val textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)

    BasicTextField(
      value = value,
      onValueChange = onValueChange,
      singleLine = true,
      textStyle = textStyle,
      cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
      keyboardOptions = keyboardOptions,
      keyboardActions = keyboardActions,
      modifier = modifier.height(44.dp),
      decorationBox = { innerTextField ->
        SearchFieldDecoration(
          isEmpty = value.isEmpty(),
          placeholder = placeholder,
          textStyle = textStyle,
          leadingIcon = leadingIcon,
          trailingIcon = trailingIcon,
          innerTextField = innerTextField
        )
      }
    )
  }

  /**
   * A [SearchField] whose text lives in [state], such as one held by a view model.
   */
  @Composable
  fun SearchField(
    state: TextFieldState,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
  ) {
    val textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)

    BasicTextField(
      state = state,
      lineLimits = TextFieldLineLimits.SingleLine,
      textStyle = textStyle,
      cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
      keyboardOptions = keyboardOptions,
      modifier = modifier.height(44.dp),
      decorator = { innerTextField ->
        SearchFieldDecoration(
          isEmpty = state.text.isEmpty(),
          placeholder = placeholder,
          textStyle = textStyle,
          leadingIcon = leadingIcon,
          trailingIcon = trailingIcon,
          innerTextField = innerTextField
        )
      }
    )
  }

  @Composable
  private fun SearchFieldDecoration(
    isEmpty: Boolean,
    placeholder: String,
    textStyle: TextStyle,
    leadingIcon: @Composable (() -> Unit)?,
    trailingIcon: @Composable (() -> Unit)?,
    innerTextField: @Composable () -> Unit
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
        .padding(start = if (leadingIcon != null) 4.dp else 16.dp, end = if (trailingIcon != null) 0.dp else 16.dp)
    ) {
      if (leadingIcon != null) {
        leadingIcon()
        Spacer(modifier = Modifier.width(4.dp))
      }

      Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier.weight(1f)
      ) {
        if (isEmpty) {
          Text(
            text = placeholder,
            style = textStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
          )
        }

        innerTextField()
      }

      trailingIcon?.invoke()
    }
  }

  /**
   * A text field label that sits at Label Medium weight once it has minimized above the input, which is what happens
   * when the field is focused or already has text in it.
   *
   * [interactionSource] must be the same instance given to the text field.
   */
  @Composable
  fun Label(
    text: String,
    hasText: Boolean,
    interactionSource: InteractionSource
  ) {
    val isFocused by interactionSource.collectIsFocusedAsState()

    Text(
      text = text,
      fontWeight = if (isFocused || hasText) FontWeight.Medium else FontWeight.Normal
    )
  }

  private fun String.createSelection(): TextRange {
    return when {
      isEmpty() -> TextRange.Zero
      else -> TextRange(length, length)
    }
  }
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun SearchFieldPreview() {
  Column {
    TextFields.SearchField(
      value = "",
      onValueChange = {},
      placeholder = "Search",
      leadingIcon = {
        IconButton(onClick = {}) {
          Icon(imageVector = SignalIcons.ArrowStart.imageVector, contentDescription = null)
        }
      },
      modifier = Modifier
        .padding(horizontal = 16.dp, vertical = 10.dp)
        .fillMaxWidth()
    )

    TextFields.SearchField(
      value = "dog",
      onValueChange = {},
      placeholder = "Search",
      leadingIcon = {
        IconButton(onClick = {}) {
          Icon(imageVector = SignalIcons.ArrowStart.imageVector, contentDescription = null)
        }
      },
      trailingIcon = {
        IconButton(onClick = {}) {
          Icon(imageVector = SignalIcons.X.imageVector, contentDescription = null)
        }
      },
      modifier = Modifier
        .padding(horizontal = 16.dp, vertical = 10.dp)
        .fillMaxWidth()
    )
  }
}
