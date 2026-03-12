/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup

/**
 * EventBus event for backup creation progress. Each subclass identifies the backup destination,
 * allowing subscribers to receive only the events they care about via the @Subscribe method
 * parameter type.
 */
sealed class BackupCreationEvent(val progress: BackupCreationProgress) {
  class RemoteEncrypted(progress: BackupCreationProgress) : BackupCreationEvent(progress)
  class LocalEncrypted(progress: BackupCreationProgress) : BackupCreationEvent(progress)
  class LocalPlaintext(progress: BackupCreationProgress) : BackupCreationEvent(progress)
}
