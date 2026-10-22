/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.routes

/**
 * Navigation requests that app settings screens send to [AppSettingsRouter].
 */
sealed interface AppSettingsNavigationEvent {

  /** Navigate to a specific screen. */
  data class NavigateToScreen(val route: AppSettingsRoute) : AppSettingsNavigationEvent

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
 * Convenience function to emit a navigate-back event to a parent emitter.
 */
fun ((AppSettingsNavigationEvent) -> Unit).navigateBack() {
  this(AppSettingsNavigationEvent.NavigateBack)
}
