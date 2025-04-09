/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

class MainNavigationViewModel : ViewModel() {
  private val detailLocationFlow = MutableSharedFlow<MainNavigationDetailLocation>()

  val detailLocation: SharedFlow<MainNavigationDetailLocation> = detailLocationFlow

  fun goTo(location: MainNavigationDetailLocation) {
    viewModelScope.launch {
      detailLocationFlow.emit(location)
    }
  }
}
