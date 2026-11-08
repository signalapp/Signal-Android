/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.fragment.compose.content
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.ui.logging.LoggingFragment

/**
 * Generic ComposeFragment which can be subclassed to build UI with compose.
 */
abstract class ComposeFragment : LoggingFragment() {

  /**
   * Whether the platform may offer to fill or save what the user types here.
   *
   * Compose registers its text fields with the autofill framework, so a password manager will offer to save the
   * contents of any screen with a field on it. Override to false where that offer is wrong -- a one-time code or the
   * name of a device is not a credential, and being asked to save it every time is noise the user can't turn off.
   */
  open val autofillEnabled: Boolean = true

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    val view = content {
      SignalTheme {
        FragmentContent()
      }
    }

    if (!autofillEnabled && Build.VERSION.SDK_INT >= 26) {
      view.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
    }

    return view
  }

  @Composable
  abstract fun FragmentContent()
}
