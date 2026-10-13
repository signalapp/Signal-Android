/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2

import android.content.Context
import android.view.View
import android.widget.EditText
import org.signal.core.ui.compose.keyboard.KeyboardSheetController
import org.signal.core.ui.compose.keyboard.KeyboardSheetKey
import org.thoughtcrime.securesms.util.ViewUtil

/**
 * Adapts [KeyboardSheetController] to the conversation's view code, which asks for keyboards from
 * click listeners rather than from composition.
 *
 * @param context Used to hide the system keyboard.
 * @param controller The controller to drive.
 */
class ChatInputController(
  private val context: Context,
  private val controller: KeyboardSheetController
) {

  private var wasKeyboardVisibleBeforeToggle: Boolean = false

  /** What [runAfterAllHidden] is waiting on. */
  private var pendingHiddenAction: (() -> Unit)? = null

  val isInputShowing: Boolean
    get() = controller.isShowing

  val isKeyboardShowing: Boolean
    get() = controller.isSystemKeyboardVisible

  /** See [KeyboardSheetController.onHostWindowShown]. */
  fun onHostWindowShown() {
    controller.onHostWindowShown()
  }

  /** See [KeyboardSheetController.onHostWindowHidden]. */
  fun onHostWindowHidden() {
    controller.onHostWindowHidden()
  }

  /** Drops anything still waiting on a hide, for a host whose view is going away. */
  fun clearPendingActions() {
    pendingHiddenAction = null
    controller.onHostWindowHidden()
  }

  /**
   * Told rather than observed: the host handles the scaffold's own stream, and only passes on the
   * two things [runAfterAllHidden] has to wait for.
   */
  fun onKeyboardVisibilityChanged(visible: Boolean) {
    if (!visible) {
      runPendingHiddenAction()
    }
  }

  /** See [onKeyboardVisibilityChanged]. */
  fun onInputHidden() {
    runPendingHiddenAction()
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

  /**
   * Runs [onHidden] once whatever is up has reported itself away, or right now if nothing is.
   * Only one action is queued at a time; a second call replaces the first.
   */
  fun runAfterAllHidden(imeTarget: EditText, onHidden: () -> Unit) {
    if (!isInputShowing && !isKeyboardShowing) {
      onHidden()
      return
    }

    pendingHiddenAction = onHidden
    hideAll(imeTarget)
  }

  private fun runPendingHiddenAction() {
    val action = pendingHiddenAction ?: return

    pendingHiddenAction = null
    action()
  }

  /**
   * @param key The keyboard to bring up, or take away if already showing.
   * @param imeTarget The field the system keyboard belongs to.
   * @param showSoftKeyOnHide Whether the system keyboard replaces [key] when it is taken away.
   */
  fun toggleInput(key: KeyboardSheetKey, imeTarget: EditText, showSoftKeyOnHide: Boolean = wasKeyboardVisibleBeforeToggle) {
    // Ours can sit behind the system keyboard rather than in place of it, where hiding it would act
    // on something the user cannot see. The system keyboard goes instead.
    if (controller.current == key && !isKeyboardShowing) {
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
}
