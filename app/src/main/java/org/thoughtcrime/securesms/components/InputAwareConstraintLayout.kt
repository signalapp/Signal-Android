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

  val isInputShowing: Boolean
    get() = input != null

  lateinit var fragmentManager: FragmentManager
  var listener: Listener? = null

  fun showSoftkey(editText: EditText) {
    ViewUtil.focusAndShowKeyboard(editText)
    hideInput(resetKeyboardGuideline = false)
  }

  fun hideAll(imeTarget: EditText) {
    ViewUtil.hideKeyboard(context, imeTarget)
    hideInput(resetKeyboardGuideline = true)
  }

  fun toggleInput(fragmentCreator: FragmentCreator, imeTarget: EditText, showSoftKeyOnHide: Boolean = false) {
    if (fragmentCreator.id == inputId) {
      if (showSoftKeyOnHide) {
        showSoftkey(imeTarget)
      } else {
        hideInput(resetKeyboardGuideline = true)
      }
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
      .runOnCommit { (input as? InputFragment)?.show() }
      .commit()

    overrideKeyboardGuidelineWithPreviousHeight()
    ViewUtil.hideKeyboard(context, imeTarget)

    listener?.onInputShown()
  }

  private fun hideInput(resetKeyboardGuideline: Boolean) {
    val inputHidden = input != null
    input?.let {
      (input as? InputFragment)?.hide()
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

  interface InputFragment {
    fun show()
    fun hide()
  }
}
