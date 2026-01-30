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
  val backStack: List<RegistrationRoute> = listOf(RegistrationRoute.Welcome),
  val sessionMetadata: NetworkController.SessionMetadata? = null,
  val sessionE164: String? = null,
  val accountEntropyPool: AccountEntropyPool? = null,
  val temporaryMasterKey: MasterKey? = null,
  val registrationLockProof: String? = null,
  val preExistingRegistrationData: PreExistingRegistrationData? = null
) : Parcelable

