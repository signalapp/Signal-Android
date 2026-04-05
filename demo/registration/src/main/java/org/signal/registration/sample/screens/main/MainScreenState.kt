/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.sample.screens.main

data class MainScreenState(
  val existingRegistrationState: ExistingRegistrationState? = null,
  val registrationExpired: Boolean = false,
  val pendingFlowState: PendingFlowState? = null
) {
  data class PendingFlowState(
    val e164: String?,
    val backstackSize: Int,
    val currentScreen: String,
    val hasSession: Boolean,
    val hasAccountEntropyPool: Boolean
  )

  data class ExistingRegistrationState(
    val phoneNumber: String,
    val aci: String,
    val pni: String,
    val aep: String,
    val pin: String?,
    val registrationLockEnabled: Boolean,
    val pinsOptedOut: Boolean,
    val temporaryMasterKey: String?
  )
}
