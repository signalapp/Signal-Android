/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.lock.v2

import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.widget.EditText
import com.google.android.material.button.MaterialButton
import org.thoughtcrime.securesms.R

/**
 * The available keyboard input types for Signal PIN entry.
 */
enum class PinKeyboardType(val code: String) {
  NUMERIC("numeric"),
  ALPHA_NUMERIC("alphaNumeric");

  companion object {
    /**
     * Gets the PinKeyboardType that is associated with a string code representation.
     */
    @JvmStatic
    fun fromCode(code: String?): PinKeyboardType = entries.firstOrNull { it.code == code } ?: NUMERIC

    /**
     * Gets the keyboard type that is associated with an [EditText]'s current input type.
     */
    @JvmStatic
    fun fromEditText(editText: EditText): PinKeyboardType = when {
      (editText.inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_NUMBER -> NUMERIC
      else -> ALPHA_NUMERIC
    }
  }

  private val inputType: Int by lazy {
    when (this) {
      NUMERIC -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
      ALPHA_NUMERIC -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
    }
  }

  private val toggleIconResource: Int by lazy {
    when (this) {
      NUMERIC -> R.drawable.ic_number_pad_conversation_filter_24
      ALPHA_NUMERIC -> R.drawable.ic_keyboard_24
    }
  }

  /**
   * Gets the opposite keyboard type to the current one.
   */
  val other: PinKeyboardType
    get() {
      return when (this) {
        NUMERIC -> ALPHA_NUMERIC
        ALPHA_NUMERIC -> NUMERIC
      }
    }

  /**
   * Configures an [EditText] and toggle button for this keyboard type.
   */
  fun applyTo(pinEditText: EditText, toggleTypeButton: MaterialButton) {
    applyInputTypeTo(pinEditText)
    applyToggleIconTo(toggleTypeButton)
  }

  /**
   * Configures an [EditText] for this keyboard type.
   */
  fun applyInputTypeTo(editText: EditText) {
    val currentInputClass = editText.inputType and InputType.TYPE_MASK_CLASS
    val desiredInputClass = this.inputType and InputType.TYPE_MASK_CLASS
    if (currentInputClass != desiredInputClass) {
      editText.getText().clear()
    }

    editText.inputType = this.inputType
    editText.transformationMethod = PasswordTransformationMethod.getInstance()
  }

  private fun applyToggleIconTo(toggleTypeButton: MaterialButton) {
    toggleTypeButton.setIconResource(this.other.toggleIconResource)
  }
}
