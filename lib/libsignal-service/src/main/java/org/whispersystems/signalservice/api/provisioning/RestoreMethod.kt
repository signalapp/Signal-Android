/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.provisioning

/**
 * Restore method chosen by user on new device after performing a quick-restore.
 */
enum class RestoreMethod {
  REMOTE_BACKUP,
  LOCAL_BACKUP,
  DEVICE_TRANSFER,
  DECLINE
}
