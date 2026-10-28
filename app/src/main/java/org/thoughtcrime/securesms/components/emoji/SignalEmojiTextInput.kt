/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.emoji

import android.text.InputType
import android.util.TypedValue
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.widget.doAfterTextChanged
import org.signal.core.ui.util.ViewUtil
import org.signal.core.util.EditTextUtil
import org.signal.emoji.EmojiTextInput
import org.signal.emoji.EmojiTextInputParams

/**
 * An [EmojiTextInput] backed by [EmojiEditText], so text fields draw Signal emoji. Provide it with
 * `LocalEmojiTextInput provides SignalEmojiTextInput`.
 */
object SignalEmojiTextInput : EmojiTextInput {

  @Composable
  override fun Content(params: EmojiTextInputParams, modifier: Modifier) {
    val currentParams by rememberUpdatedState(params)
    var focused by remember { mutableStateOf(false) }

    // Emitted once composition settles, since the view can take focus before the label is collecting interactions.
    LaunchedEffect(params.interactionSource) {
      var focus: FocusInteraction.Focus? = null
      snapshotFlow { focused }.collect { hasFocus ->
        if (hasFocus && focus == null) {
          focus = FocusInteraction.Focus().also { params.interactionSource.emit(it) }
        } else if (!hasFocus) {
          focus?.let { params.interactionSource.emit(FocusInteraction.Unfocus(it)) }
          focus = null
        }
      }
    }

    EditText(
      params = params,
      currentParams = { currentParams },
      onFocusChanged = { focused = it },
      modifier = modifier
        .fillMaxWidth()
        .then(params.focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
    )
  }

  @Composable
  private fun EditText(params: EmojiTextInputParams, currentParams: () -> EmojiTextInputParams, onFocusChanged: (Boolean) -> Unit, modifier: Modifier) {
    AndroidView(
      factory = { context ->
        EmojiEditText(context).apply {
          background = null
          setPadding(0, 0, 0, 0)

          doAfterTextChanged { text ->
            val value = text?.toString().orEmpty()
            if (value != currentParams().value) {
              currentParams().onValueChange(value)
            }
          }

          addOnFocusChangeListener { _, hasFocus ->
            onFocusChanged(hasFocus)
            if (hasFocus) {
              ViewUtil.focusAndMoveCursorToEndAndOpenKeyboard(this)
            }
          }

          setOnEditorActionListener { _, actionId, _ ->
            if (actionId != EditorInfo.IME_NULL) {
              currentParams().onImeAction()
              true
            } else {
              false
            }
          }
        }
      },
      update = { view ->
        if (view.text.toString() != params.value) {
          view.setText(params.value)
          view.setSelection(params.value.length)
        }

        val inputType = params.inputType()
        if (view.inputType != inputType) {
          view.inputType = inputType
        }

        if ((view.maxLines == 1) != params.singleLine) {
          view.isSingleLine = params.singleLine
        }
        view.imeOptions = params.imeAction.toImeOptions()
        view.isEnabled = params.enabled
        ViewCompat.setAutofillHints(view, *listOfNotNull(params.autofillHint).toTypedArray())

        if (params.textStyle.fontSize.isSp) {
          view.setTextSize(TypedValue.COMPLEX_UNIT_SP, params.textStyle.fontSize.value)
        }
        view.setTextColor(params.textStyle.color.toArgb())
        EditTextUtil.setCursorColor(view, params.cursorColor.toArgb())
      },
      modifier = modifier
    )
  }

  private fun EmojiTextInputParams.inputType(): Int {
    val capitalizationFlag = when (capitalization) {
      KeyboardCapitalization.Characters -> InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
      KeyboardCapitalization.Words -> InputType.TYPE_TEXT_FLAG_CAP_WORDS
      KeyboardCapitalization.Sentences -> InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
      else -> 0
    }
    val multiLineFlag = if (singleLine) 0 else InputType.TYPE_TEXT_FLAG_MULTI_LINE

    return InputType.TYPE_CLASS_TEXT or capitalizationFlag or multiLineFlag
  }

  private fun ImeAction.toImeOptions(): Int {
    return when (this) {
      ImeAction.Done -> EditorInfo.IME_ACTION_DONE
      ImeAction.Next -> EditorInfo.IME_ACTION_NEXT
      ImeAction.Previous -> EditorInfo.IME_ACTION_PREVIOUS
      ImeAction.Go -> EditorInfo.IME_ACTION_GO
      ImeAction.Search -> EditorInfo.IME_ACTION_SEARCH
      ImeAction.Send -> EditorInfo.IME_ACTION_SEND
      ImeAction.None -> EditorInfo.IME_ACTION_NONE
      else -> EditorInfo.IME_ACTION_UNSPECIFIED
    }
  }
}
