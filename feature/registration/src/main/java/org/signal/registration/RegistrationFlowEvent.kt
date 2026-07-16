/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration

import org.signal.core.models.AccountEntropyPool
import org.signal.core.models.MasterKey
import org.signal.core.util.censor

sealed interface RegistrationFlowEvent {
  /**
   * Navigate to a specific screen.
   *
   * @param popCurrent Remove the current screen from the backstack
   */
  data class NavigateToScreen(val route: RegistrationRoute, val popCurrent: Boolean = false) : RegistrationFlowEvent

  /** Navigate back one screen. */
  data object NavigateBack : RegistrationFlowEvent

  /** Pop the back stack back to an existing screen, removing everything above it. Replaces the current screen if the route isn't on the stack. */
  data class NavigateBackToScreen(val route: RegistrationRoute) : RegistrationFlowEvent

  /** We've encountered some irrecoverable state where the best course of action is to completely reset registration. */
  data object ResetState : RegistrationFlowEvent

  /** An update has been made to the ongoing registration session.  */
  data class SessionUpdated(val session: NetworkController.SessionMetadata) : RegistrationFlowEvent

  /** The e164 associated with this registration attempt has been updated.  */
  data class E164Chosen(val e164: String) : RegistrationFlowEvent

  /**
   * The user has successfully registered.
   *
   * @param storageCapable Whether the server reports that this account already has SVR/PIN data, as returned in the
   * registration response. Used later (e.g. when skipping a restore) to decide between PIN entry and PIN creation.
   */
  data class Registered(val accountEntropyPool: AccountEntropyPool, val storageCapable: Boolean) : RegistrationFlowEvent {
    override fun toString(): String = "Registered(accountEntropyPool=${accountEntropyPool.displayValue.censor()}, storageCapable=$storageCapable)"
  }

  /** The master key has been restored from SVR. */
  data class MasterKeyRestoredFromSvr(val masterKey: MasterKey) : RegistrationFlowEvent {
    override fun toString(): String = "MasterKeyRestoredFromSvr(masterKey=${masterKey.toString().censor()})"
  }

  /** We've discovered that RRP-based registration is not possible for this account. */
  data object RecoveryPasswordInvalid : RegistrationFlowEvent

  /** The user selected (or cleared) a restore option before entering their phone number. */
  data class PendingRestoreOptionSelected(val option: PendingRestoreOption?) : RegistrationFlowEvent

  /** Provisioning data was received from the old device. Carries the token used to notify it of our restore-method choice. */
  data class RestoreMethodTokenReceived(val token: String) : RegistrationFlowEvent {
    override fun toString(): String = "RestoreMethodTokenReceived(token=${token.censor()})"
  }

  /** An AEP was manually input by the user. It has not yet been verified against the server. */
  data class UserSuppliedAepSubmitted(val aep: AccountEntropyPool) : RegistrationFlowEvent {
    override fun toString(): String = "UserSuppliedAepSubmitted(aep=${aep.displayValue.censor()})"
  }

  /** An AEP that was previously manually input by the user (see [UserSuppliedAepSubmitted]) has been validated. We should use it as the canonical AEP.  */
  data class UserSuppliedAepVerified(val aep: AccountEntropyPool) : RegistrationFlowEvent {
    override fun toString(): String = "UserSuppliedAepVerified(aep=${aep.displayValue.censor()})"
  }

  /** Registration has been completed. Will finalize any pending state, then navigate to flow's conclusion. */
  data object RegistrationComplete : RegistrationFlowEvent
}
