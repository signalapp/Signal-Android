/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.util

import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationRoute

/**
 * Convenience function to emit a navigation event to a parentEmitter.
 */
fun ((RegistrationFlowEvent) -> Unit).navigateTo(route: RegistrationRoute) {
  this(RegistrationFlowEvent.NavigateToScreen(route))
}

/**
 * Convenience function to emit a navigate-back event to a parentEmitter.
 */
fun ((RegistrationFlowEvent) -> Unit).navigateBack() {
  this(RegistrationFlowEvent.NavigateBack)
}
