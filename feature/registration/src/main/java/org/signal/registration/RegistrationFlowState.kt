/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler
import org.signal.core.models.AccountEntropyPool
import org.signal.core.models.MasterKey
import org.signal.core.util.censor
import org.signal.registration.util.AccountEntropyPoolParceler
import org.signal.registration.util.DebugLoggableModel
import org.signal.registration.util.MasterKeyParceler

@Parcelize
@TypeParceler<MasterKey?, MasterKeyParceler>
@TypeParceler<AccountEntropyPool?, AccountEntropyPoolParceler>
data class RegistrationFlowState(
  /** The navigation stack. Controls what screen we're on and what the backstack looks like. */
  val backStack: List<RegistrationRoute> = listOf(RegistrationRoute.Welcome),

  /** The metadata for the currently-active registration session. */
  val sessionMetadata: NetworkController.SessionMetadata? = null,

  /** The e164 associated with the [sessionMetadata]. */
  val sessionE164: String? = null,

  /** The AEP we generated as part of this registration. */
  val accountEntropyPool: AccountEntropyPool? = null,

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

  /** If true, the ViewModel is still deciding whether to restore a previous flow or start fresh. */
  val isRestoringNavigationState: Boolean = true
) : Parcelable, DebugLoggableModel() {
  override fun toSafeString(): String {
    return "RegistrationFlowState(backStack=${backStack.joinToString()}, sessionMetadata=${sessionMetadata.let { "present" }}, sessionE164=$sessionE164, accountEntropyPool=${accountEntropyPool?.displayValue?.censor()}, temporaryMasterKey=${temporaryMasterKey?.toString()?.censor()}, preExistingRegistrationData=${preExistingRegistrationData?.let { "present" }}, doNotAttemptRecoveryPassword=$doNotAttemptRecoveryPassword, pendingRestoreOption=$pendingRestoreOption, unverifiedRestoredAep=${unverifiedRestoredAep?.displayValue?.censor()}, isRestoringNavigation=$isRestoringNavigationState)"
  }
}
