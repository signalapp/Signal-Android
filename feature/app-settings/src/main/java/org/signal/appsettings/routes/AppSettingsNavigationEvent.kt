/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.routes

import kotlin.reflect.KClass

/**
 * Navigation requests that app settings screens send to [AppSettingsRouter].
 */
sealed interface AppSettingsNavigationEvent {

  /**
   * Navigate to a specific screen.
   *
   * @param replacesDetail whether [route] replaces every screen open in the detail pane, as a section opened from the
   *   settings home does, rather than stacking on top of them.
   * @param popUpTo screens to close before [route] opens. Naming a screen that is already on top of what is left, such
   *   as [route] itself, goes back to it as it was rather than opening it again.
   */
  data class NavigateToScreen(
    val route: AppSettingsRoute,
    val replacesDetail: Boolean = false,
    val popUpTo: PopUpTo? = null
  ) : AppSettingsNavigationEvent

  /** Navigate back one screen. */
  data object NavigateBack : AppSettingsNavigationEvent
}

/**
 * Closes every screen above the topmost one of type [route], and that one too when [inclusive]. Matched by type, so
 * the screen's arguments don't need to be known. Does nothing when no such screen is open.
 */
data class PopUpTo(val route: KClass<out AppSettingsRoute>, val inclusive: Boolean = false)

/**
 * Convenience function to emit a navigation event to a parent emitter.
 */
fun ((AppSettingsNavigationEvent) -> Unit).navigateTo(route: AppSettingsRoute, popUpTo: PopUpTo? = null) {
  this(AppSettingsNavigationEvent.NavigateToScreen(route, popUpTo = popUpTo))
}

/**
 * Convenience function to emit a navigation event that replaces whatever is open in the detail pane with [route].
 */
fun ((AppSettingsNavigationEvent) -> Unit).openSection(route: AppSettingsRoute) {
  this(AppSettingsNavigationEvent.NavigateToScreen(route, replacesDetail = true))
}

/**
 * Convenience function to emit a navigate-back event to a parent emitter.
 */
fun ((AppSettingsNavigationEvent) -> Unit).navigateBack() {
  this(AppSettingsNavigationEvent.NavigateBack)
}
