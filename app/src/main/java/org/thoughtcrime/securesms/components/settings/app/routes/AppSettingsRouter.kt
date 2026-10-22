/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.routes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Router which manages what screen we are displaying in app settings. Screen view models are handed [onEvent] as their
 * parent event emitter and navigate with [navigateTo] / [navigateBack].
 *
 * This gives a single point to navigate to a new page, but assumes that the actual backstack of routes
 * will be handled elsewhere. This just emits routing requests.
 */
class AppSettingsRouter : ViewModel() {

  private val _events = MutableSharedFlow<AppSettingsNavigationEvent>()

  val events: SharedFlow<AppSettingsNavigationEvent> = _events.asSharedFlow()

  fun onEvent(event: AppSettingsNavigationEvent) {
    viewModelScope.launch {
      _events.emit(event)
    }
  }
}
