/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration

import org.signal.core.models.AccountEntropyPool
import org.signal.core.models.MasterKey
import org.signal.core.util.censor
import org.signal.network.api.RegistrationApiV2.SessionMetadata
import org.signal.network.api.RegistrationApiV2.VerificationCodeTransport
import kotlin.time.Duration.Companion.seconds

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
  data class SessionUpdated(val session: SessionMetadata) : RegistrationFlowEvent

  /** The e164 associated with this registration attempt has been updated.  */
  data class E164Chosen(val e164: String) : RegistrationFlowEvent

  /** The user's phone number was successfully verified with the given code. Retained so later PIN screens can warn if the user re-enters it as their PIN. */
  data class VerificationCodeAccepted(val code: String) : RegistrationFlowEvent {
    override fun toString(): String = "VerificationCodeAccepted(code=${code.censor()})"
  }

  /**
   * A verification code was requested for [e164] — either fulfilled or rejected as rate-limited. Records the epoch-millis
   * times at which the server will allow the next SMS and call requests, since the response reports both regardless of
   * which transport was used. A null timestamp means the response carried no information for that transport.
   */
  data class VerificationCodeRequested(
    val e164: String,
    val nextSmsAllowedTimestamp: Long?,
    val nextCallAllowedTimestamp: Long?
  ) : RegistrationFlowEvent {
    companion object {
      /** How long we assume the server will disallow another request when the response doesn't include a duration for the requested transport. */
      private val DEFAULT_RETRY_WINDOW = 60.seconds

      fun from(e164: String, transport: VerificationCodeTransport, session: SessionMetadata, now: Long): VerificationCodeRequested {
        // We'll only fallback to a default if we're requesting that specific transport
        val nextSmsIn = session.nextSms?.seconds ?: DEFAULT_RETRY_WINDOW.takeIf { transport == VerificationCodeTransport.SMS }
        val nextCallIn = session.nextCall?.seconds ?: DEFAULT_RETRY_WINDOW.takeIf { transport == VerificationCodeTransport.VOICE }

        return VerificationCodeRequested(
          e164 = e164,
          nextSmsAllowedTimestamp = nextSmsIn?.let { now + it.inWholeMilliseconds },
          nextCallAllowedTimestamp = nextCallIn?.let { now + it.inWholeMilliseconds }
        )
      }
    }
  }

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
