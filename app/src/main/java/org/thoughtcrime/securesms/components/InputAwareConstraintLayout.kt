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
  private var wasKeyboardVisibleBeforeToggle: Boolean = false
  private val listeners: MutableSet<Listener> = mutableSetOf()

  val isInputShowing: Boolean
    get() = input != null

  lateinit var fragmentManager: FragmentManager

  fun addInputListener(listener: Listener) {
    listeners.add(listener)
  }

  fun remoteInputListener(listener: Listener) {
    listeners.remove(listener)
  }

  fun showSoftkey(editText: EditText) {
    ViewUtil.focusAndShowKeyboard(editText)
    hideInput(resetKeyboardGuideline = false)
  }

  fun hideAll(imeTarget: EditText) {
    wasKeyboardVisibleBeforeToggle = false
    ViewUtil.hideKeyboard(context, imeTarget)
    hideInput(resetKeyboardGuideline = true)
  }

  fun runAfterAllHidden(imeTarget: EditText, onHidden: () -> Unit) {
    if (isInputShowing || isKeyboardShowing) {
      val listener = object : Listener, KeyboardStateListener {
        override fun onInputHidden() {
          onHidden()
          remoteInputListener(this)
          removeKeyboardStateListener(this)
        }

        override fun onKeyboardHidden() {
          onHidden()
          remoteInputListener(this)
          removeKeyboardStateListener(this)
        }

        override fun onInputShown(fragmentCreatorId: Int) = Unit
        override fun onKeyboardShown() = Unit
      }

      addInputListener(listener)
      addKeyboardStateListener(listener)
      hideAll(imeTarget)
    } else {
      onHidden()
    }
  }

  fun toggleInput(fragmentCreator: FragmentCreator, imeTarget: EditText, showSoftKeyOnHide: Boolean = wasKeyboardVisibleBeforeToggle) {
    if (fragmentCreator.id == inputId) {
      if (showSoftKeyOnHide) {
        showSoftkey(imeTarget)
      } else {
        hideInput(resetKeyboardGuideline = true)
      }
    } else {
      wasKeyboardVisibleBeforeToggle = isKeyboardShowing
      hideInput(resetKeyboardGuideline = false)
      showInput(fragmentCreator, imeTarget)
    }
  }

  fun hideInput() {
    hideInput(resetKeyboardGuideline = true)
    wasKeyboardVisibleBeforeToggle = false
  }

  fun hideKeyboard(imeTarget: EditText, keepHeightOverride: Boolean = false) {
    if (isKeyboardShowing) {
      if (keepHeightOverride) {
        overrideKeyboardGuidelineWithPreviousHeight()
      }
      ViewUtil.hideKeyboard(context, imeTarget)
    }
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

    listeners.forEach { it.onInputShown(fragmentCreator.id) }
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
      listeners.forEach { it.onInputHidden() }
    }
  }

  interface FragmentCreator {
    val id: Int
    fun create(): Fragment
  }

  interface Listener {
    fun onInputShown(fragmentCreatorId: Int)
    fun onInputHidden()
  }

  interface InputFragment {
    fun show()
    fun hide()
  }
}
