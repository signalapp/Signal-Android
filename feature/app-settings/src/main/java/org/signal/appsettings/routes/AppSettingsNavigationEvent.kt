/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.routes

/**
 * Navigation requests that app settings screens send to [AppSettingsRouter].
 */
sealed interface AppSettingsNavigationEvent {

  /**
   * Navigate to a specific screen.
   *
   * @param replacesDetail whether [route] replaces every screen open in the detail pane, as a section opened from the
   *   settings home does, rather than stacking on top of them.
   */
  data class NavigateToScreen(val route: AppSettingsRoute, val replacesDetail: Boolean = false) : AppSettingsNavigationEvent

  /** Navigate back one screen. */
  data object NavigateBack : AppSettingsNavigationEvent
}

/**
 * Convenience function to emit a navigation event to a parent emitter.
 */
fun ((AppSettingsNavigationEvent) -> Unit).navigateTo(route: AppSettingsRoute) {
  this(AppSettingsNavigationEvent.NavigateToScreen(route))
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
