/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration

sealed interface RegistrationFlowEvent {
  data class NavigateToScreen(val route: RegistrationRoute) : RegistrationFlowEvent
  data object NavigateBack : RegistrationFlowEvent
  data object ResetState : RegistrationFlowEvent
  data class SessionUpdated(val session: NetworkController.SessionMetadata) : RegistrationFlowEvent
  data class E164Chosen(val e164: String) : RegistrationFlowEvent
}
