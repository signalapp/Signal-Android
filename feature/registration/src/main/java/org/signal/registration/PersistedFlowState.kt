/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration

import kotlinx.serialization.Serializable
import org.signal.core.models.AccountEntropyPool
import org.signal.core.models.MasterKey
import org.signal.network.api.RegistrationApiV2.SessionMetadata

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
  val sessionMetadata: SessionMetadata?,
  val sessionE164: String?,
  val submittedVerificationCode: String? = null,
  val doNotAttemptRecoveryPassword: Boolean,
  val pendingRestoreOption: PendingRestoreOption? = null,
  val restoredAepValue: String? = null,
  val restoreMethodToken: String? = null,
  val storageCapable: Boolean = false,
  val smsVerificationCodeRequest: VerificationCodeRequest? = null,
  val callVerificationCodeRequest: VerificationCodeRequest? = null
)

/**
 * Extracts the persistable fields from a [RegistrationFlowState].
 */
fun RegistrationFlowState.toPersistedFlowState(): PersistedFlowState {
  return PersistedFlowState(
    backStack = backStack,
    sessionMetadata = sessionMetadata,
    sessionE164 = sessionE164,
    submittedVerificationCode = submittedVerificationCode,
    doNotAttemptRecoveryPassword = doNotAttemptRecoveryPassword,
    pendingRestoreOption = pendingRestoreOption,
    restoredAepValue = unverifiedRestoredAep?.value,
    restoreMethodToken = restoreMethodToken,
    storageCapable = storageCapable,
    smsVerificationCodeRequest = lastSmsVerificationCodeRequest,
    callVerificationCodeRequest = lastCallVerificationCodeRequest
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
    submittedVerificationCode = submittedVerificationCode,
    accountEntropyPool = accountEntropyPool,
    temporaryMasterKey = temporaryMasterKey,
    preExistingRegistrationData = preExistingRegistrationData,
    doNotAttemptRecoveryPassword = doNotAttemptRecoveryPassword,
    pendingRestoreOption = pendingRestoreOption,
    unverifiedRestoredAep = restoredAepValue?.let { AccountEntropyPool(it) },
    restoreMethodToken = restoreMethodToken,
    storageCapable = storageCapable,
    lastSmsVerificationCodeRequest = smsVerificationCodeRequest,
    lastCallVerificationCodeRequest = callVerificationCodeRequest
  )
}
