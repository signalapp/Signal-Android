/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.megaphone.Megaphone
import org.thoughtcrime.securesms.megaphone.Megaphones

class MainNavigationViewModel : ViewModel() {
  private val megaphoneRepository = AppDependencies.megaphoneRepository

  private val detailLocationFlow = MutableSharedFlow<MainNavigationDetailLocation>()
  val detailLocation: SharedFlow<MainNavigationDetailLocation> = detailLocationFlow

  private val internalMegaphone = MutableStateFlow(Megaphone.NONE)
  val megaphone: StateFlow<Megaphone> = internalMegaphone

  private val internalSnackbar = MutableStateFlow<SnackbarState?>(null)
  val snackbar: StateFlow<SnackbarState?> = internalSnackbar

  private val internalNavigationEvents = MutableSharedFlow<NavigationEvent>()
  val navigationEvents: Flow<NavigationEvent> = internalNavigationEvents

  fun goTo(location: MainNavigationDetailLocation) {
    viewModelScope.launch {
      detailLocationFlow.emit(location)
    }
  }

  fun goToCameraFirstStoryCapture() {
    viewModelScope.launch {
      internalNavigationEvents.emit(NavigationEvent.STORY_CAMERA_FIRST)
    }
  }

  fun getNextMegaphone() {
    megaphoneRepository.getNextMegaphone { next ->
      internalMegaphone.update { next ?: Megaphone.NONE }
    }
  }

  fun setSnackbar(snackbarState: SnackbarState?) {
    internalSnackbar.update { snackbarState }
  }

  fun onMegaphoneSnoozed(event: Megaphones.Event) {
    megaphoneRepository.markSeen(event)
    internalMegaphone.update { Megaphone.NONE }
  }

  fun onMegaphoneCompleted(event: Megaphones.Event) {
    internalMegaphone.update { Megaphone.NONE }
    megaphoneRepository.markFinished(event)
  }

  fun onMegaphoneVisible(visible: Megaphone) {
    megaphoneRepository.markVisible(visible.event)
  }

  enum class NavigationEvent {
    STORY_CAMERA_FIRST
  }
}
