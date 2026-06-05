/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration

import kotlinx.serialization.Serializable
import org.signal.core.models.AccountEntropyPool
import org.signal.core.models.MasterKey

/**
 * A serializable snapshot of [RegistrationFlowState] fields that need to survive app kills.
 *
 * Fields like [RegistrationFlowState.accountEntropyPool] and [RegistrationFlowState.temporaryMasterKey]
 * are reconstructed from dedicated proto fields, not from this JSON snapshot.
 * [RegistrationFlowState.preExistingRegistrationData] is loaded from permanent storage.
 */
@Serializable
data class PersistedFlowState(
  val backStack: List<RegistrationRoute>,
  val sessionMetadata: NetworkController.SessionMetadata?,
  val sessionE164: String?,
  val doNotAttemptRecoveryPassword: Boolean,
  val pendingRestoreOption: PendingRestoreOption? = null,
  val restoredAepValue: String? = null
)

/**
 * Extracts the persistable fields from a [RegistrationFlowState].
 */
fun RegistrationFlowState.toPersistedFlowState(): PersistedFlowState {
  return PersistedFlowState(
    backStack = backStack,
    sessionMetadata = sessionMetadata,
    sessionE164 = sessionE164,
    doNotAttemptRecoveryPassword = doNotAttemptRecoveryPassword,
    pendingRestoreOption = pendingRestoreOption,
    restoredAepValue = unverifiedRestoredAep?.value
  )
}

/**
 * Reconstructs a full [RegistrationFlowState] from persisted data and separately-stored fields.
 *
 * @param accountEntropyPool Restored from the proto's dedicated `accountEntropyPool` field.
 * @param temporaryMasterKey Restored from the proto's dedicated `temporaryMasterKey` field.
 * @param preExistingRegistrationData Loaded from permanent storage via [StorageController.getPreExistingRegistrationData].
 */
fun PersistedFlowState.toRegistrationFlowState(
  accountEntropyPool: AccountEntropyPool?,
  temporaryMasterKey: MasterKey?,
  preExistingRegistrationData: PreExistingRegistrationData?
): RegistrationFlowState {
  return RegistrationFlowState(
    backStack = backStack,
    sessionMetadata = sessionMetadata,
    sessionE164 = sessionE164,
    accountEntropyPool = accountEntropyPool,
    temporaryMasterKey = temporaryMasterKey,
    preExistingRegistrationData = preExistingRegistrationData,
    doNotAttemptRecoveryPassword = doNotAttemptRecoveryPassword,
    pendingRestoreOption = pendingRestoreOption,
    unverifiedRestoredAep = restoredAepValue?.let { AccountEntropyPool(it) }
  )
}
