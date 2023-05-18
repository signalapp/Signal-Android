/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components

import android.content.Context
import android.util.AttributeSet
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.ViewUtil

/**
 * A flavor of [InsetAwareConstraintLayout] that allows "replacing" the keyboard with our
 * own input fragment.
 */
class InputAwareConstraintLayout @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : InsetAwareConstraintLayout(context, attrs, defStyleAttr) {

  private var inputId: Int? = null
  private var input: Fragment? = null

  lateinit var fragmentManager: FragmentManager
  var listener: Listener? = null

  fun showSoftkey(editText: EditText) {
    ViewUtil.focusAndShowKeyboard(editText)
    hideInput(resetKeyboardGuideline = false)
  }

  fun toggleInput(fragmentCreator: FragmentCreator, imeTarget: EditText, toggled: (Boolean) -> Unit = { }) {
    if (fragmentCreator.id == inputId) {
      hideInput(resetKeyboardGuideline = true)
      toggled(false)
    } else {
      hideInput(resetKeyboardGuideline = false)
      showInput(fragmentCreator, imeTarget)
    }
  }

  fun hideInput() {
    hideInput(resetKeyboardGuideline = true)
  }

  private fun showInput(fragmentCreator: FragmentCreator, imeTarget: EditText) {
    inputId = fragmentCreator.id
    input = fragmentCreator.create()

    fragmentManager
      .beginTransaction()
      .replace(R.id.input_container, input!!)
      .commit()

    overrideKeyboardGuidelineWithPreviousHeight()
    ViewUtil.hideKeyboard(context, imeTarget)

    listener?.onInputShown()
  }

  private fun hideInput(resetKeyboardGuideline: Boolean) {
    val inputHidden = input != null
    input?.let {
      fragmentManager
        .beginTransaction()
        .remove(it)
        .commit()
    }
    input = null
    inputId = null

    if (resetKeyboardGuideline) {
      resetKeyboardGuideline()
    } else {
      clearKeyboardGuidelineOverride()
    }

    if (inputHidden) {
      listener?.onInputHidden()
    }
  }

  interface FragmentCreator {
    val id: Int
    fun create(): Fragment
  }

  interface Listener {
    fun onInputShown()
    fun onInputHidden()
  }
}
