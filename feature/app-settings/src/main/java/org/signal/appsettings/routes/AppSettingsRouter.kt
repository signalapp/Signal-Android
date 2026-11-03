/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.routes

import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.split.ListDetailBackStack
import org.signal.core.ui.compose.split.ListDetailEvents
import org.signal.core.ui.compose.split.ListDetailNavigator
import org.signal.core.ui.compose.split.detailDepth
import org.signal.core.ui.compose.split.hasDetail
import org.signal.core.ui.navigation.ResultEventBus

/**
 * Router which manages what screen we are displaying in app settings. Screen view models are handed [onEvent] as their
 * parent event emitter and navigate with [navigateTo] / [navigateBack].
 *
 * Fragment hosts collect [events] and map them onto their nav graph. [AppSettingsNavigation] instead applies them to
 * [navigator], which owns the app settings back stack.
 */
class AppSettingsRouter(private val savedStateHandle: SavedStateHandle) : ViewModel() {

  companion object {
    private const val BACK_STACK_KEY = "app_settings_back_stack"
    private const val OPENED_DIRECTLY_KEY = "app_settings_opened_directly"
  }

  private val _events = Channel<AppSettingsNavigationEvent>(Channel.BUFFERED)

  /** Buffered until collected, so that navigation sent while the host isn't listening is still carried out. */
  val events: Flow<AppSettingsNavigationEvent> = _events.receiveAsFlow()

  /** Carries results between screens, such as a picked avatar back to edit profile. */
  val resultBus = ResultEventBus()

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

  /**
   * Whether the section on screen was opened straight from outside of settings, so that going back from it leaves
   * settings, as though nothing else had been open.
   */
  private var openedDirectly: Boolean
    get() = savedStateHandle[OPENED_DIRECTLY_KEY] ?: false
    set(value) {
      savedStateHandle[OPENED_DIRECTLY_KEY] = value
    }

  /** Opens [route] on its own, for a link into settings from elsewhere in the app. */
  fun openDirectly(route: AppSettingsRoute) {
    navigate(AppSettingsNavigationEvent.NavigateToScreen(route, replacesDetail = true))
    openedDirectly = true
  }

  fun onEvent(event: AppSettingsNavigationEvent) {
    viewModelScope.launch {
      _events.send(event)
    }
  }

  /**
   * Applies [event] to [navigator]. Returns false when it asks to go back out of app settings altogether, which is up
   * to the host.
   *
   * @param isSplitPane whether the list is showing beside the detail, so that backing out of the detail's last screen
   *   would leave an empty pane.
   */
  internal fun navigate(event: AppSettingsNavigationEvent, isSplitPane: Boolean = false): Boolean {
    when (event) {
      is AppSettingsNavigationEvent.NavigateToScreen -> Snapshot.withMutableSnapshot {
        if (event.replacesDetail || event.route == AppSettingsRoute.Empty) {
          navigator.processEvent(ListDetailEvents.ExitDetail)
          openedDirectly = false
        }

        event.popUpTo?.let { navigator[AppSettingsHomeRoute].popUpTo(it) }

        if (event.route != AppSettingsRoute.Empty) {
          navigator.processEvent(ListDetailEvents.Push(event.route))
        }
      }

      AppSettingsNavigationEvent.NavigateBack -> {
        val stack = navigator[AppSettingsHomeRoute]
        // Beside the list, or opened on its own, a section's first screen is never closed by going back.
        if (!stack.hasDetail || (stack.detailDepth == 1 && (isSplitPane || openedDirectly))) {
          return false
        }

        navigator.processEvent(ListDetailEvents.Back)
      }
    }

    return true
  }
}

/** Pops the detail screens [popUpTo] names, never reaching the list beneath them. */
private fun ListDetailBackStack.popUpTo(popUpTo: PopUpTo) {
  val firstDetailIndex = lastIndex - detailDepth + 1
  val index = indexOfLast { popUpTo.route.isInstance(it) }
  if (index < firstDetailIndex) {
    return
  }

  val retained = if (popUpTo.inclusive) index else index + 1
  while (size > retained) {
    removeAt(lastIndex)
  }
}
