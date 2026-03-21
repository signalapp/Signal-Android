/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration

import kotlinx.serialization.Serializable

/**
 * Represents a restore option the user selected before entering their phone number.
 * After phone number entry, the registration flow will navigate to the appropriate
 * restore flow based on this selection.
 */
@Serializable
enum class PendingRestoreOption {
  LocalBackup,
  RemoteBackup
}
