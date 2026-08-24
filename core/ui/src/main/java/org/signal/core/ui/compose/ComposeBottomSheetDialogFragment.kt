/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import org.signal.core.ui.FixedRoundedCornerBottomSheetDialogFragment
import org.signal.core.ui.compose.LocalFragmentManager
import org.signal.core.ui.compose.theme.SignalTheme

/**
 * Base class for bottom sheets whose content is entirely Compose.
 * Provides [LocalFragmentManager] and [SignalTheme] to the composition, and delegates
 * content rendering to the abstract [SheetContent] composable.
 */
abstract class ComposeBottomSheetDialogFragment : FixedRoundedCornerBottomSheetDialogFragment() {

  protected open val forceDarkTheme = false

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    return ComposeView(requireContext()).apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setContent {
        val isDark = if (forceDarkTheme) {
          true
        } else {
          LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        }
        CompositionLocalProvider(LocalFragmentManager provides childFragmentManager) {
          SignalTheme(isDarkMode = isDark) {
            // No navigation bar padding here: BottomSheetBehavior already bottom-pads the sheet
            // for the navigation bar (paddingBottomSystemWindowInsets) once the window is
            // edge-to-edge, so padding again would double up. The keyboard inset excludes the
            // navigation bar for the same reason.
            Surface(
              modifier = Modifier.windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars)),
              shape = RoundedCornerShape(cornerRadius.dp, cornerRadius.dp),
              color = SignalTheme.colors.colorSurface1,
              contentColor = MaterialTheme.colorScheme.onSurface
            ) {
              SheetContent()
            }
          }
        }
      }
    }
  }

  @Composable
  abstract fun SheetContent()
}
