/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.DialogFragment
import org.signal.core.ui.R
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.ui.initializeScreenshotSecurity
import org.signal.core.ui.util.ThemeUtil

/**
 * Generic Compose-based full screen dialog fragment.
 *
 * Expects [R.attr.fullScreenDialogStyle] to be defined in your app theme, pointing to a style
 * suitable for full screen dialogs.
 */
abstract class ComposeFullScreenDialogFragment : DialogFragment() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val fullScreenDialogStyle = ThemeUtil.getThemedResourceId(requireContext(), R.attr.fullScreenDialogStyle)
    setStyle(STYLE_NO_FRAME, fullScreenDialogStyle)
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    return ComposeView(requireContext()).apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setContent {
        SignalTheme {
          DialogContent()
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    requireDialog().window?.initializeScreenshotSecurity()
  }

  @Composable
  abstract fun DialogContent()
}
