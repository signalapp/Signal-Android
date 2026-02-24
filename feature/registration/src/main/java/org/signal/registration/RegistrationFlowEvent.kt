/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration

import org.signal.core.models.AccountEntropyPool
import org.signal.core.models.MasterKey

sealed interface RegistrationFlowEvent {
  /** Navigate to a specific screen. */
  data class NavigateToScreen(val route: RegistrationRoute) : RegistrationFlowEvent

  /** Navigate back one screen. */
  data object NavigateBack : RegistrationFlowEvent

  /** We've encountered some irrecoverable state where the best course of action is to completely reset registration. */
  data object ResetState : RegistrationFlowEvent

  /** An update has been made to the ongoing registration session.  */
  data class SessionUpdated(val session: NetworkController.SessionMetadata) : RegistrationFlowEvent

  /** The e164 associated with this registration attempt has been updated.  */
  data class E164Chosen(val e164: String) : RegistrationFlowEvent

  /** The user has successfully registered. */
  data class Registered(val accountEntropyPool: AccountEntropyPool) : RegistrationFlowEvent

  /** The master key has been restored from SVR. */
  data class MasterKeyRestoredFromSvr(val masterKey: MasterKey) : RegistrationFlowEvent

  /** We've discovered that RRP-based registration is not possible for this account. */
  data object RecoveryPasswordInvalid : RegistrationFlowEvent
}
