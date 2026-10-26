/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.routes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.split.ListDetailEvents
import org.signal.core.ui.compose.split.ListDetailNavigator
import org.signal.core.ui.compose.split.hasDetail

/**
 * Router which manages what screen we are displaying in app settings. Screen view models are handed [onEvent] as their
 * parent event emitter and navigate with [navigateTo] / [navigateBack].
 *
 * Fragment hosts collect [events] and map them onto their nav graph. [AppSettingsNavigation] instead applies them to
 * [navigator], which owns the app settings back stack.
 */
class AppSettingsRouter(savedStateHandle: SavedStateHandle) : ViewModel() {

  companion object {
    private const val BACK_STACK_KEY = "app_settings_back_stack"
  }

  private val _events = MutableSharedFlow<AppSettingsNavigationEvent>()

  val events: SharedFlow<AppSettingsNavigationEvent> = _events.asSharedFlow()

  val navigator = ListDetailNavigator<AppSettingsHomeRoute, AppSettingsRoute>(
    savedStateHandle = savedStateHandle,
    scope = viewModelScope,
    stackKeys = mapOf(AppSettingsHomeRoute to BACK_STACK_KEY),
    initialRoot = AppSettingsHomeRoute
  )

  /**
   * The section open in the detail pane: the bottom-most detail on the stack, whatever has been opened on top of it.
   */
  val selectedRoute: StateFlow<AppSettingsRoute?> = navigator.snapshotsOf(AppSettingsHomeRoute) { getOrNull(1) as? AppSettingsRoute }
    .stateIn(viewModelScope, SharingStarted.Eagerly, null)

  fun onEvent(event: AppSettingsNavigationEvent) {
    viewModelScope.launch {
      _events.emit(event)
    }
  }

  /**
   * Applies [event] to [navigator]. Returns false when it asks to go back from the home screen, which leaves app
   * settings altogether and so is up to the host.
   */
  internal fun navigate(event: AppSettingsNavigationEvent): Boolean {
    when (event) {
      is AppSettingsNavigationEvent.NavigateToScreen -> {
        if (event.route == AppSettingsRoute.Empty) {
          navigator.processEvent(ListDetailEvents.ExitDetail)
        } else {
          navigator.processEvent(ListDetailEvents.Push(event.route))
        }
      }

      AppSettingsNavigationEvent.NavigateBack -> {
        if (!navigator[AppSettingsHomeRoute].hasDetail) {
          return false
        }

        navigator.processEvent(ListDetailEvents.Back)
      }
    }

    return true
  }
}
