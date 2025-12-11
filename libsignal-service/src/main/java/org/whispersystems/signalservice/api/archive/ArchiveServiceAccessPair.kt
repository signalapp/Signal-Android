/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.archive

import org.signal.core.models.backup.MediaRootBackupKey
import org.signal.core.models.backup.MessageBackupKey

/**
 * A convenient container for passing around both a message and media archive service credential.
 */
data class ArchiveServiceAccessPair(
  val messageBackupAccess: ArchiveServiceAccess<MessageBackupKey>,
  val mediaBackupAccess: ArchiveServiceAccess<MediaRootBackupKey>
)
