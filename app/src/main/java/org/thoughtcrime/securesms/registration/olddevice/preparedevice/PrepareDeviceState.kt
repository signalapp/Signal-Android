/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.olddevice.preparedevice

/**
 * State for the PrepareDevice screen shown during quick restore flow.
 *
 * @param lastBackupTimestamp The timestamp of the last backup in milliseconds, or 0 if never backed up.
 */
data class PrepareDeviceState(
  val lastBackupTimestamp: Long = 0
)
