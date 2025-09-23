/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.main

import androidx.annotation.Px
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class InsetsViewModel : ViewModel() {
  private val internalInsets = MutableStateFlow(Insets.Zero)
  val insets: StateFlow<Insets> = internalInsets

  fun updateInsets(insets: Insets) {
    internalInsets.update { insets }
  }

  data class Insets(
    @param:Px val statusBar: Float,
    @param:Px val navBar: Float
  ) {
    companion object {
      val Zero = Insets(0f, 0f)
    }
  }
}

@Composable
fun InsetsViewModelUpdater(
  insetsViewModel: InsetsViewModel = viewModel { InsetsViewModel() }
) {
  val statusBarInsets = WindowInsets.statusBars
  val navigationBarInsets = WindowInsets.navigationBars

  val statusBarPadding = statusBarInsets.asPaddingValues()
  val navigationBarPadding = navigationBarInsets.asPaddingValues()
  val density = LocalDensity.current

  LaunchedEffect(statusBarPadding, navigationBarPadding, density) {
    val statusBarPx = with(density) {
      (statusBarPadding.calculateTopPadding() + statusBarPadding.calculateBottomPadding()).toPx()
    }

    val navBarPx = with(density) {
      (navigationBarPadding.calculateTopPadding() + navigationBarPadding.calculateBottomPadding()).toPx()
    }

    insetsViewModel.updateInsets(
      InsetsViewModel.Insets(
        statusBar = statusBarPx,
        navBar = navBarPx
      )
    )
  }
}
