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
  data class NavigateToScreen @JvmOverloads constructor(
    val route: AppSettingsRoute,
    val replacesDetail: Boolean = false,
    val popUpTo: PopUpTo? = null
  ) : AppSettingsNavigationEvent

  /**
   * Navigate back one screen.
   *
   * @param popUpTo the screens to close instead of only the top one, for a screen that has to close itself whatever is
   *   open above it. Nothing is closed when no such screen is open.
   */
  data class NavigateBack @JvmOverloads constructor(val popUpTo: PopUpTo? = null) : AppSettingsNavigationEvent

  /**
   * Leave app settings altogether, whatever screens are open.
   */
  data object Close : AppSettingsNavigationEvent

  /**
   * The screen on top has unsaved edits. Until [EndEditSession], or until that screen is closed, picking another
   * section or leaving settings from the list asks to discard them first. Going back from the screen itself is left to
   * the screen.
   */
  data object BeginEditSession : AppSettingsNavigationEvent

  /** The edits from [BeginEditSession] were saved or thrown away. */
  data object EndEditSession : AppSettingsNavigationEvent
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
 * Convenience function to emit an event marking the screen on top as having unsaved edits.
 */
fun ((AppSettingsNavigationEvent) -> Unit).beginEditSession() {
  this(AppSettingsNavigationEvent.BeginEditSession)
}

/**
 * Convenience function to emit an event marking the edits from [beginEditSession] as saved or thrown away.
 */
fun ((AppSettingsNavigationEvent) -> Unit).endEditSession() {
  this(AppSettingsNavigationEvent.EndEditSession)
}

/**
 * Convenience function to emit an event that leaves app settings altogether.
 */
fun ((AppSettingsNavigationEvent) -> Unit).close() {
  this(AppSettingsNavigationEvent.Close)
}

/**
 * Convenience function to emit a navigate-back event to a parent emitter.
 */
fun ((AppSettingsNavigationEvent) -> Unit).navigateBack(popUpTo: PopUpTo? = null) {
  this(AppSettingsNavigationEvent.NavigateBack(popUpTo))
}
