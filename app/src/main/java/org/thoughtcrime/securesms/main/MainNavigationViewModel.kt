/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.main

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class MainNavigationViewModel : ViewModel() {
  private val detailLocationFlow = MutableStateFlow<MainNavigationDetailLocation>(MainNavigationDetailLocation.Empty)

  val detailLocation: StateFlow<MainNavigationDetailLocation> = detailLocationFlow

  fun goTo(location: MainNavigationDetailLocation) {
    detailLocationFlow.update { location }
  }
}
