/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.main

import android.os.Parcelable
import androidx.annotation.Px
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import kotlinx.parcelize.Parcelize
import org.thoughtcrime.securesms.window.isSplitPane

@Parcelize
data class VerticalInsets(
  @param:Px val statusBar: Float,
  @param:Px val navBar: Float
) : Parcelable {
  companion object {
    val Zero = VerticalInsets(0f, 0f)
  }
}

@Composable
fun rememberVerticalInsets(): State<VerticalInsets> {
  val statusBarInsets = WindowInsets.statusBars
  val navigationBarInsets = WindowInsets.navigationBars

  val statusBarPadding = statusBarInsets.asPaddingValues()
  val navigationBarPadding = navigationBarInsets.asPaddingValues()
  val density = LocalDensity.current

  val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass

  val insets = rememberSaveable { mutableStateOf(VerticalInsets.Zero) }
  val updated = remember(statusBarInsets, navigationBarInsets, windowSizeClass) {
    insets.value = if (windowSizeClass.isSplitPane()) {
      VerticalInsets.Zero
    } else {
      calculateAndUpdateInsets(density, statusBarPadding, navigationBarPadding)
    }

    0
  }

  return insets
}

private fun calculateAndUpdateInsets(
  density: Density,
  statusBarPadding: PaddingValues,
  navigationBarPadding: PaddingValues
): VerticalInsets {
  val statusBarPx = with(density) {
    (statusBarPadding.calculateTopPadding() + statusBarPadding.calculateBottomPadding()).toPx()
  }

  val navBarPx = with(density) {
    (navigationBarPadding.calculateTopPadding() + navigationBarPadding.calculateBottomPadding()).toPx()
  }

  return VerticalInsets(
    statusBar = statusBarPx,
    navBar = navBarPx
  )
}
