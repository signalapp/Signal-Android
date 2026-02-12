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
import org.signal.registration.util.AccountEntropyPoolParceler
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
  val doNotAttemptRecoveryPassword: Boolean = false
) : Parcelable

