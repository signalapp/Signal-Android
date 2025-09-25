/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.routes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/**
 * Router which manages what screen we are displaying in app settings. Underneath, this is a ViewModel
 * that is tied to the top-level parent, so that all screens throughout the app settings can access it.
 *
 * This gives a single point to navigate to a new page, but assumes that the actual backstack of routes
 * will be handled elsewhere. This just emits routing requests.
 */
class AppSettingsRouter() : ViewModel() {

  val currentRoute = MutableSharedFlow<AppSettingsRoute>()

  fun navigateTo(route: AppSettingsRoute) {
    viewModelScope.launch {
      currentRoute.emit(route)
    }
  }
}
