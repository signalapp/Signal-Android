/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2

import android.content.Context
import android.view.View
import android.widget.EditText
import kotlinx.coroutines.withTimeoutOrNull
import org.thoughtcrime.securesms.components.compose.mediakeyboard.MediaKeyboardController
import org.thoughtcrime.securesms.components.compose.mediakeyboard.MediaKeyboardKey
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.awaitAfterNextLayout
import kotlin.time.Duration.Companion.milliseconds

/** Longest a keyboard's exit is waited on. Comfortably past the platform's own hide animation. */
private val SETTLE_TIMEOUT = 500.milliseconds

/**
 * Adapts [MediaKeyboardController] to the conversation's view code, which asks for keyboards from
 * click listeners rather than from composition.
 *
 * @param context Used to hide the system keyboard.
 * @param controller The controller to drive.
 */
class ChatInputController(
  private val context: Context,
  private val controller: MediaKeyboardController
) {

  private var wasKeyboardVisibleBeforeToggle: Boolean = false

  private val listeners: MutableSet<Listener> = mutableSetOf()
  private val keyboardStateListeners: MutableSet<KeyboardStateListener> = mutableSetOf()

  val isInputShowing: Boolean
    get() = controller.isShowing

  val isKeyboardShowing: Boolean
    get() = controller.isSystemKeyboardVisible

  fun addInputListener(listener: Listener) {
    listeners.add(listener)
  }

  fun removeInputListener(listener: Listener) {
    listeners.remove(listener)
  }

  fun addKeyboardStateListener(listener: KeyboardStateListener) {
    keyboardStateListeners.add(listener)
  }

  fun removeKeyboardStateListener(listener: KeyboardStateListener) {
    keyboardStateListeners.remove(listener)
  }

  /** Drops everything still listening, for a host whose view is going away. */
  fun clearListeners() {
    listeners.clear()
    keyboardStateListeners.clear()
  }

  fun onKeyboardVisibilityChanged(visible: Boolean) {
    keyboardStateListeners.toList().forEach {
      if (visible) it.onKeyboardShown() else it.onKeyboardHidden()
    }
  }

  fun onKeyboardAnimationEnded() {
    keyboardStateListeners.toList().forEach { it.onKeyboardAnimationEnded() }
  }

  fun onInputShown(key: MediaKeyboardKey) {
    listeners.toList().forEach { it.onInputShown(key) }
  }

  fun onInputHidden() {
    listeners.toList().forEach { it.onInputHidden() }
  }

  /** @param imeTarget The field to bring the system keyboard up for, which need not be the input panel's. */
  fun showSoftkey(imeTarget: View) {
    controller.hideForSystemKeyboard()
    ViewUtil.focusAndShowKeyboard(imeTarget)
  }

  fun hideAll(imeTarget: EditText) {
    wasKeyboardVisibleBeforeToggle = false
    controller.hide()
    ViewUtil.hideKeyboard(context, imeTarget)
  }

  fun hideInput() {
    wasKeyboardVisibleBeforeToggle = false
    controller.hide()
  }

  /**
   * Unconditional, since [isKeyboardShowing] only knows about keyboards that claim space. A floating
   * one reports no inset to read it from and still needs putting away.
   */
  fun hideKeyboard(imeTarget: EditText) {
    ViewUtil.hideKeyboard(context, imeTarget)
  }

  fun runAfterAllHidden(imeTarget: EditText, onHidden: () -> Unit) {
    if (isInputShowing || isKeyboardShowing) {
      val listener = object : Listener, KeyboardStateListener {
        override fun onInputHidden() {
          onHidden()
          removeInputListener(this)
          removeKeyboardStateListener(this)
        }

        override fun onKeyboardHidden() {
          onHidden()
          removeInputListener(this)
          removeKeyboardStateListener(this)
        }

        override fun onInputShown(key: MediaKeyboardKey) = Unit
        override fun onKeyboardShown() = Unit
      }

      addInputListener(listener)
      addKeyboardStateListener(listener)
      hideAll(imeTarget)
    } else {
      onHidden()
    }
  }

  /**
   * Like [runAfterAllHidden], but suspends until the keyboards have finished animating out rather
   * than returning as soon as the hide has been asked for. For callers that measure themselves
   * against the content area, which stays shrunk for the length of that animation.
   *
   * Gives up after [SETTLE_TIMEOUT]. A keyboard that never reports its exit should leave a caller
   * measuring against a stale content area, not stranded.
   *
   * @param contentView The area the keyboards resize. The settle itself lands in the middle of an
   *   inset dispatch, a frame before the space is handed back, so this is waited on as well.
   */
  suspend fun hideAllAndAwaitSettled(imeTarget: EditText, contentView: View) {
    if (controller.isSettled) {
      return
    }

    hideAll(imeTarget)
    withTimeoutOrNull(SETTLE_TIMEOUT) {
      controller.awaitSettled()
      contentView.awaitAfterNextLayout()
    }
  }

  /**
   * @param key The keyboard to bring up, or take away if already showing.
   * @param imeTarget The field the system keyboard belongs to.
   * @param showSoftKeyOnHide Whether the system keyboard replaces [key] when it is taken away.
   */
  fun toggleInput(key: MediaKeyboardKey, imeTarget: EditText, showSoftKeyOnHide: Boolean = wasKeyboardVisibleBeforeToggle) {
    if (controller.current == key) {
      if (showSoftKeyOnHide) {
        showSoftkey(imeTarget)
      } else {
        hideInput()
      }
    } else {
      wasKeyboardVisibleBeforeToggle = isKeyboardShowing
      controller.show(key)
      ViewUtil.hideKeyboard(context, imeTarget)
    }
  }

  interface Listener {
    fun onInputShown(key: MediaKeyboardKey)
    fun onInputHidden()
  }

  interface KeyboardStateListener {
    fun onKeyboardShown()
    fun onKeyboardHidden()
    fun onKeyboardAnimationEnded() = Unit
  }
}
