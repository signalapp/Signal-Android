/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

object ClearableTextField {
  /**
   * Configures how the "characters remaining" countdown is displayed.
   */
  data class CountdownConfig(
    /** The number of characters remaining before the countdown is displayed. */
    val displayThreshold: Int,

    /** The number of characters remaining before the countdown is displayed as warning. */
    val warnThreshold: Int
  )
}

/**
 * A text field with an optional clear button that appears when focused and [clearable] is true.
 *
 * Also supports displaying a character countdown when the character count is approaching a limit.
 */
@Composable
fun ClearableTextField(
  value: String,
  onValueChange: (String) -> Unit,
  hint: String,
  clearContentDescription: String,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  textStyle: TextStyle = LocalTextStyle.current,
  leadingIcon: @Composable (() -> Unit)? = null,
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
  keyboardActions: KeyboardActions = KeyboardActions.Default,
  singleLine: Boolean = false,
  clearable: Boolean = true,
  onClear: () -> Unit = { onValueChange("") },
  hasClearableContent: () -> Boolean = { value.isNotEmpty() },
  charactersRemainingBeforeLimit: Int = Int.MAX_VALUE,
  countdownConfig: ClearableTextField.CountdownConfig? = null,
  colors: TextFieldColors = defaultTextFieldColors()
) {
  ClearableTextField(
    value = value,
    onValueChange = onValueChange,
    clearContentDescription = clearContentDescription,
    modifier = modifier,
    enabled = enabled,
    textStyle = textStyle,
    label = { Text(hint) },
    leadingIcon = leadingIcon,
    keyboardOptions = keyboardOptions,
    keyboardActions = keyboardActions,
    singleLine = singleLine,
    clearable = clearable,
    onClear = onClear,
    hasClearableContent = hasClearableContent,
    charactersRemainingBeforeLimit = charactersRemainingBeforeLimit,
    countdownConfig = countdownConfig,
    colors = colors
  )
}

/**
 * A text field with an optional clear button that appears when focused and [clearable] is true.
 *
 * Also supports displaying a character countdown when the character count is approaching a limit.
 */
@Composable
fun ClearableTextField(
  value: String,
  onValueChange: (String) -> Unit,
  clearContentDescription: String,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  textStyle: TextStyle = LocalTextStyle.current,
  label: @Composable (() -> Unit)? = null,
  placeholder: @Composable (() -> Unit)? = null,
  leadingIcon: @Composable (() -> Unit)? = null,
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
  keyboardActions: KeyboardActions = KeyboardActions.Default,
  singleLine: Boolean = false,
  clearable: Boolean = true,
  onClear: () -> Unit = { onValueChange("") },
  hasClearableContent: () -> Boolean = { value.isNotEmpty() },
  charactersRemainingBeforeLimit: Int = Int.MAX_VALUE,
  countdownConfig: ClearableTextField.CountdownConfig? = null,
  colors: TextFieldColors = defaultTextFieldColors()
) {
  var focused by remember { mutableStateOf(false) }
  val displayCountdown = countdownConfig != null && charactersRemainingBeforeLimit <= countdownConfig.displayThreshold

  val clearButton: @Composable () -> Unit = {
    ClearButton(
      visible = focused && hasClearableContent(),
      onClick = onClear,
      contentDescription = clearContentDescription
    )
  }

  Column(modifier = modifier) {
    TextFields.TextField(
      value = value,
      onValueChange = onValueChange,
      textStyle = textStyle,
      label = label,
      placeholder = placeholder,
      enabled = enabled,
      singleLine = singleLine,
      keyboardActions = keyboardActions,
      keyboardOptions = keyboardOptions,
      modifier = Modifier
        .fillMaxWidth()
        .onFocusChanged { focused = it.hasFocus && clearable },
      colors = colors,
      leadingIcon = leadingIcon,
      trailingIcon = if (clearable) clearButton else null
    )

    AnimatedVisibility(
      visible = displayCountdown,
      modifier = Modifier.align(Alignment.End)
    ) {
      val errorThresholdExceeded = countdownConfig != null && charactersRemainingBeforeLimit <= countdownConfig.warnThreshold
      Text(
        text = "$charactersRemainingBeforeLimit",
        style = MaterialTheme.typography.bodySmall,
        color = if (errorThresholdExceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(top = 4.dp, end = 16.dp)
      )
    }
  }
}

@Composable
private fun ClearButton(
  visible: Boolean,
  onClick: () -> Unit,
  contentDescription: String
) {
  AnimatedVisibility(
    visible = visible,
    enter = fadeIn(),
    exit = fadeOut()
  ) {
    IconButton(
      onClick = onClick
    ) {
      Icon(
        painter = SignalIcons.X.painter,
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.onSurface
      )
    }
  }
}

@Composable
private fun defaultTextFieldColors(): TextFieldColors = TextFieldDefaults.colors(
  unfocusedLabelColor = MaterialTheme.colorScheme.outline,
  unfocusedIndicatorColor = MaterialTheme.colorScheme.outline
)

@DayNightPreviews
@Composable
private fun ClearableTextFieldPreview() {
  Previews.Preview {
    val focusRequester = remember { FocusRequester() }

    Column(modifier = Modifier.padding(16.dp)) {
      ClearableTextField(
        value = "",
        onValueChange = {},
        hint = "without content",
        clearContentDescription = ""
      )

      Spacer(modifier = Modifier.size(16.dp))

      ClearableTextField(
        value = "foo bar",
        onValueChange = {},
        hint = "with content",
        clearContentDescription = ""
      )

      Spacer(modifier = Modifier.size(16.dp))

      ClearableTextField(
        value = "",
        onValueChange = {},
        hint = "disabled",
        clearContentDescription = "",
        enabled = false
      )

      Spacer(modifier = Modifier.size(16.dp))

      ClearableTextField(
        value = "",
        onValueChange = {},
        hint = "focused without content",
        clearContentDescription = "",
        modifier = Modifier.focusRequester(focusRequester)
      )
    }

    LaunchedEffect(Unit) {
      focusRequester.requestFocus()
    }
  }
}

@DayNightPreviews
@Composable
private fun ClearableTextFieldCharacterCountPreview() {
  Previews.Preview {
    val countdownConfig = ClearableTextField.CountdownConfig(displayThreshold = 100, warnThreshold = 10)

    Column(modifier = Modifier.padding(16.dp)) {
      ClearableTextField(
        value = "Character count normal state",
        onValueChange = {},
        hint = "countdown shown",
        clearContentDescription = "Clear",
        charactersRemainingBeforeLimit = 50,
        countdownConfig = countdownConfig
      )

      Spacer(modifier = Modifier.size(16.dp))

      val focusRequester = remember { FocusRequester() }

      ClearableTextField(
        value = "Very long text showing the character count warning state",
        onValueChange = {},
        hint = "countdown warning",
        clearContentDescription = "Clear",
        charactersRemainingBeforeLimit = 8,
        countdownConfig = countdownConfig,
        modifier = Modifier.focusRequester(focusRequester)
      )

      LaunchedEffect(Unit) {
        focusRequester.requestFocus()
      }

      Spacer(modifier = Modifier.size(16.dp))

      ClearableTextField(
        value = "No character count shown",
        onValueChange = {},
        hint = "no countdown shown",
        clearContentDescription = "Clear",
        charactersRemainingBeforeLimit = 150,
        countdownConfig = countdownConfig
      )
    }
  }
}
