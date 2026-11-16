/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.emoji

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.VisualTransformation
import org.signal.core.util.BreakIteratorCompat

/**
 * The text input inside an [EmojiTextField]: everything that edits and draws the text, without the label, indicator or
 * container around it.
 */
fun interface EmojiTextInput {

  @Composable
  fun Content(params: EmojiTextInputParams, modifier: Modifier)

  companion object {
    /** A plain [BasicTextField], which draws system emoji. Used wherever nothing better is provided, such as previews and tests. */
    val Default = EmojiTextInput { params, modifier ->
      BasicTextField(
        value = params.value,
        onValueChange = { params.onValueChange(params.maxCharacters?.let(it::takeCharacters) ?: it) },
        enabled = params.enabled,
        singleLine = params.singleLine,
        textStyle = params.textStyle,
        cursorBrush = SolidColor(params.cursorColor),
        keyboardOptions = KeyboardOptions(capitalization = params.capitalization, imeAction = params.imeAction),
        keyboardActions = KeyboardActions { params.onImeAction() },
        interactionSource = params.interactionSource,
        modifier = modifier
          .then(params.focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
          .then(params.autofillHint?.let { hint -> Modifier.semantics { contentType = ContentType(hint) } } ?: Modifier)
      )
    }
  }
}

private fun String.takeCharacters(max: Int): String {
  val breakIterator = BreakIteratorCompat.getInstance()
  breakIterator.setText(this)
  return breakIterator.take(max).toString()
}

/**
 * What an [EmojiTextInput] has to honor. [interactionSource] must receive focus interactions, since the label and
 * indicator around the input are driven by it, and a request through [focusRequester] must focus the input itself.
 *
 * @param jumboEmoji draws Signal emoji at their larger size, for text that's mostly emoji and set large.
 * @param maxCharacters the most characters the input accepts, counting each emoji as one, or null for no limit.
 */
class EmojiTextInputParams(
  val value: String,
  val onValueChange: (String) -> Unit,
  val enabled: Boolean,
  val singleLine: Boolean,
  val textStyle: TextStyle,
  val cursorColor: Color,
  val capitalization: KeyboardCapitalization,
  val imeAction: ImeAction,
  val onImeAction: () -> Unit,
  val autofillHint: String?,
  val focusRequester: FocusRequester?,
  val interactionSource: MutableInteractionSource,
  val jumboEmoji: Boolean = false,
  val maxCharacters: Int? = null
)

/**
 * Supplies the input [EmojiTextField] edits with. Compose text fields can't draw Signal's emoji, so the app provides a
 * View-backed input that can.
 */
val LocalEmojiTextInput = staticCompositionLocalOf { EmojiTextInput.Default }

/**
 * A Material filled text field whose text is drawn by [LocalEmojiTextInput], so it shows Signal emoji when the app
 * provides an input that can.
 *
 * @param onImeAction called for the keyboard's action. Defaults to moving focus on for [ImeAction.Next], and to nothing otherwise.
 * @param autofillHint the kind of content the field holds, as one of the `androidx.autofill.HintConstants`.
 * @param focusRequester focuses the input. Use this rather than a `focusRequester` modifier, which would land on the
 *   decoration around the input instead.
 */
@Composable
fun EmojiTextField(
  value: String,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  singleLine: Boolean = false,
  label: @Composable (() -> Unit)? = null,
  placeholder: @Composable (() -> Unit)? = null,
  trailingIcon: @Composable (() -> Unit)? = null,
  supportingText: @Composable (() -> Unit)? = null,
  capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
  imeAction: ImeAction = ImeAction.Default,
  onImeAction: (() -> Unit)? = null,
  autofillHint: String? = null,
  focusRequester: FocusRequester? = null,
  interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
  val colors = TextFieldDefaults.colors()
  val focused by interactionSource.collectIsFocusedAsState()
  val focusManager = LocalFocusManager.current

  val textColor = when {
    !enabled -> colors.disabledTextColor
    focused -> colors.focusedTextColor
    else -> colors.unfocusedTextColor
  }

  val params = EmojiTextInputParams(
    value = value,
    onValueChange = onValueChange,
    enabled = enabled,
    singleLine = singleLine,
    textStyle = LocalTextStyle.current.merge(MaterialTheme.typography.bodyLarge).copy(color = textColor),
    cursorColor = colors.cursorColor,
    capitalization = capitalization,
    imeAction = imeAction,
    onImeAction = onImeAction ?: { if (imeAction == ImeAction.Next) focusManager.moveFocus(FocusDirection.Next) },
    autofillHint = autofillHint,
    focusRequester = focusRequester,
    interactionSource = interactionSource
  )

  val input = LocalEmojiTextInput.current

  Box(
    modifier = modifier
      .defaultMinSize(minWidth = TextFieldDefaults.MinWidth, minHeight = TextFieldDefaults.MinHeight)
  ) {
    TextFieldDefaults.DecorationBox(
      value = value,
      innerTextField = { input.Content(params, Modifier) },
      enabled = enabled,
      singleLine = singleLine,
      visualTransformation = VisualTransformation.None,
      interactionSource = interactionSource,
      label = label,
      placeholder = placeholder,
      trailingIcon = trailingIcon,
      supportingText = supportingText,
      colors = colors
    )
  }
}
