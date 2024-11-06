/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.archive

import org.whispersystems.signalservice.api.backup.MediaRootBackupKey
import org.whispersystems.signalservice.api.backup.MessageBackupKey

/**
 * A convenient container for passing around both a message and media archive service credential.
 */
data class ArchiveServiceAccessPair(
  val messageBackupAccess: ArchiveServiceAccess<MessageBackupKey>,
  val mediaBackupAccess: ArchiveServiceAccess<MediaRootBackupKey>
)
