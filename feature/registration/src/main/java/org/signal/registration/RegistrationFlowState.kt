/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler
import kotlinx.serialization.Serializable
import org.signal.core.models.AccountEntropyPool
import org.signal.core.models.MasterKey
import org.signal.core.util.censor
import org.signal.network.api.RegistrationApiV2.SessionMetadata
import org.signal.registration.util.AccountEntropyPoolParceler
import org.signal.registration.util.MasterKeyParceler
import org.signal.registration.util.NullableSessionMetadataParceler

@Parcelize
@TypeParceler<MasterKey?, MasterKeyParceler>
@TypeParceler<AccountEntropyPool?, AccountEntropyPoolParceler>
@TypeParceler<SessionMetadata?, NullableSessionMetadataParceler>
data class RegistrationFlowState(
  /** The navigation stack. Controls what screen we're on and what the backstack looks like. */
  val backStack: List<RegistrationRoute> = listOf(RegistrationRoute.Welcome),

  /** The metadata for the currently-active registration session. */
  val sessionMetadata: SessionMetadata? = null,

  /** The e164 associated with the [sessionMetadata]. */
  val sessionE164: String? = null,

  /** The verification code the user successfully used to verify their phone number, if they went through SMS/call verification. */
  val submittedVerificationCode: String? = null,

  /** The AEP we generated as part of this registration. */
  val accountEntropyPool: AccountEntropyPool? = null,

  /** Whether the server reported that this account already has SVR/PIN data, captured from the registration response. */
  val storageCapable: Boolean = false,

  /** The master key we restored from SVR. Needed for initial storage service restore, but afterwards we'll generate a new one. */
  val temporaryMasterKey: MasterKey? = null,

  /** If set, indicates that this is a re-registration. It contains a bundle of data related to that previous registration. */
  val preExistingRegistrationData: PreExistingRegistrationData? = null,

  /** If true, do not attempt any flows where we generate RRP's. Create a session instead. */
  val doNotAttemptRecoveryPassword: Boolean = false,

  /** If set, the user selected a restore option before entering their phone number. After phone number entry, the flow will navigate to this restore flow. */
  val pendingRestoreOption: PendingRestoreOption? = null,

  /** The AEP obtained via manual entry for local/remote backup restore. May or may not be valid for the current phone number. */
  val unverifiedRestoredAep: AccountEntropyPool? = null,

  /**
   * If non-null, identifies the old device's quick-restore listener. Set when we receive a [NetworkController.ProvisioningMessage]
   * from the old device, then used to notify the old device of the user's restore-method selection so its UX can update.
   */
  val restoreMethodToken: String? = null,

  /** Details around the last time we requested an SMS verification code and when we're allowed to request another. */
  val lastSmsVerificationCodeRequest: VerificationCodeRequest? = null,

  /** Details around the last time we requested a voice-call verification code and when we're allowed to request another. */
  val lastCallVerificationCodeRequest: VerificationCodeRequest? = null,

  /** If true, the ViewModel is still deciding whether to restore a previous flow or start fresh. */
  val isRestoringNavigationState: Boolean = true
) : Parcelable {
  override fun toString(): String {
    return "RegistrationFlowState(backStack=${backStack.joinToString()}, sessionMetadata=$sessionMetadata, sessionE164=$sessionE164, submittedVerificationCode=${submittedVerificationCode?.censor()}, accountEntropyPool=${accountEntropyPool?.displayValue?.censor()}, storageCapable=$storageCapable, temporaryMasterKey=${temporaryMasterKey?.toString()?.censor()}, preExistingRegistrationData=$preExistingRegistrationData, doNotAttemptRecoveryPassword=$doNotAttemptRecoveryPassword, pendingRestoreOption=$pendingRestoreOption, unverifiedRestoredAep=${unverifiedRestoredAep?.displayValue?.censor()}, restoreMethodToken=${restoreMethodToken?.censor()}, lastSmsVerificationCodeRequest=$lastSmsVerificationCodeRequest, lastCallVerificationCodeRequest=$lastCallVerificationCodeRequest, isRestoringNavigation=$isRestoringNavigationState)"
  }
}

/** A verification code request we made for [e164], recording the epoch-millis time at which the server will allow another request over the same transport. */
@Parcelize
@Serializable
data class VerificationCodeRequest(
  val e164: String,
  val nextAllowedRequestTime: Long
) : Parcelable
