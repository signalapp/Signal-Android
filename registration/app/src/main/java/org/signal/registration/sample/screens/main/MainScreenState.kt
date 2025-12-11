/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.sample.screens.main

data class MainScreenState(
  val existingRegistrationState: ExistingRegistrationState? = null
) {
  data class ExistingRegistrationState(
    val phoneNumber: String,
    val aci: String,
    val pni: String,
    val aep: String,
    val pin: String?,
    val registrationLockEnabled: Boolean
  )
}
