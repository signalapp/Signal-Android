/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

@file:JvmName("HostInsets")

package org.thoughtcrime.securesms.components.settings

import android.view.View
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import org.signal.core.ui.isSplitPane
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.components.settings.app.AppSettingsNavigationActivity
import org.thoughtcrime.securesms.components.settings.conversation.ConversationSettingsNavHostFragment
import org.thoughtcrime.securesms.util.SystemWindowInsetsSetter
import org.thoughtcrime.securesms.util.doOnEachLayout

/**
 * Whether this fragment's host already insets it for the system bars, so the fragment must not apply them as well.
 *
 * All host windows are edge-to-edge, so fragments normally inset their own toolbar and content. The exceptions are
 * fragments embedded directly in [MainActivity]'s Compose scaffolding, which insets those containers itself — except
 * the conversation-settings detail entry ([ConversationSettingsNavHostFragment]), which leaves insets to its fragments
 * so the toolbar scroll tint can reach behind the status bar — and the detail pane of a split-pane
 * [AppSettingsNavigationActivity], which insets both panes itself. Fragments shown inside a dialog have their own
 * window, which no host insets, so they always apply insets themselves.
 */
fun Fragment.isInsetByHost(): Boolean {
  val parents = generateSequence(parentFragment) { it.parentFragment }
  if (parents.any { it is DialogFragment && it.showsDialog }) {
    return false
  }

  return when (activity) {
    is MainActivity -> parents.none { it is ConversationSettingsNavHostFragment }
    is AppSettingsNavigationActivity -> resources.isSplitPane()
    else -> false
  }
}

/**
 * Keeps [view] clear of the system bars and keyboard. A host that already insets for the system bars ([isInsetByHost])
 * leaves only the keyboard to [view].
 */
fun Fragment.insetForSafeAreaWithKeyboard(view: View) {
  val insetTypes = if (isInsetByHost()) WindowInsetsCompat.Type.ime() else SystemWindowInsetsSetter.SAFE_AREA_WITH_KEYBOARD
  SystemWindowInsetsSetter.attach(view, viewLifecycleOwner, insetTypes)
}

/**
 * Pads [toolbar] for the status bar and grows it by the same amount, so its tint reaches behind the status bar while
 * its content keeps the height it was laid out with. Grown rather than wrapped, which can measure the bar's content
 * shorter than the bar itself after a relaunch.
 */
fun insetToolbarForStatusBar(toolbar: View, lifecycleOwner: LifecycleOwner) {
  val contentHeight = toolbar.layoutParams.height
  SystemWindowInsetsSetter.attach(toolbar, lifecycleOwner, WindowInsetsCompat.Type.statusBars())

  if (contentHeight <= 0) {
    return
  }

  toolbar.doOnEachLayout {
    val height = contentHeight + toolbar.paddingTop + toolbar.paddingBottom
    if (toolbar.layoutParams.height != height) {
      toolbar.post { toolbar.updateLayoutParams { this.height = height } }
    }
  }
}
