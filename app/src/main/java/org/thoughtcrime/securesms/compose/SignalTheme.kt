/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.compose

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import org.signal.core.ui.compose.theme.ExtendedColors
import org.thoughtcrime.securesms.util.TextSecurePreferences

private typealias CoreSignalTheme = org.signal.core.ui.compose.theme.SignalTheme

@Composable
fun SignalTheme(
  isDarkMode: Boolean = LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES,
  content: @Composable () -> Unit
) {
  val context = LocalContext.current
  val incognitoKeyboardEnabled = remember {
    TextSecurePreferences.isIncognitoKeyboardEnabled(context)
  }

  org.signal.core.ui.compose.theme.SignalTheme(
    isDarkMode = isDarkMode,
    incognitoKeyboardEnabled = incognitoKeyboardEnabled,
    content = content
  )
}

object SignalTheme {
  val colors: ExtendedColors
    @Composable
    get() = CoreSignalTheme.colors
}
