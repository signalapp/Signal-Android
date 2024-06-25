/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui

/**
 * An ordered list of checkpoints of the registration process.
 * This is used for screens to know when to advance, as well as restoring state after process death.
 */
enum class RegistrationCheckpoint {
  INITIALIZATION,
  PERMISSIONS_GRANTED,
  BACKUP_RESTORED_OR_SKIPPED,
  PUSH_NETWORK_AUDITED,
  PHONE_NUMBER_CONFIRMED,
  PIN_CONFIRMED,
  CHALLENGE_RECEIVED,
  CHALLENGE_COMPLETED,
  VERIFICATION_CODE_REQUESTED,
  VERIFICATION_CODE_ENTERED,
  PIN_ENTERED,
  VERIFICATION_CODE_VALIDATED,
  SERVICE_REGISTRATION_COMPLETED,
  LOCAL_REGISTRATION_COMPLETE
}
