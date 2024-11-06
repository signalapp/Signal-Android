/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.archive

import org.whispersystems.signalservice.api.backup.BackupKey

/**
 * Key and credential combo needed to perform backup operations on the server.
 */
class ArchiveServiceAccess<T : BackupKey>(
  val credential: ArchiveServiceCredential,
  val backupKey: T
)
