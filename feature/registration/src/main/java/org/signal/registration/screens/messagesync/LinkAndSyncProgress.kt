/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.messagesync

import org.signal.core.util.ByteSize

/**
 * Progress events emitted while restoring a link-and-sync message backup from the primary device.
 * Each value maps to a UI state for the [MessageSyncScreen].
 */
sealed interface LinkAndSyncProgress {
  /** Waiting for the primary device to make the backup available (no byte progress yet). */
  data object Waiting : LinkAndSyncProgress

  /** Downloading the backup from the primary/CDN. */
  data class Downloading(val bytesDownloaded: ByteSize, val totalBytes: ByteSize) : LinkAndSyncProgress

  /** Importing/restoring messages from the downloaded backup. */
  data object Restoring : LinkAndSyncProgress

  /** The link-and-sync restore completed (or there was nothing to restore). */
  data object Complete : LinkAndSyncProgress

  /** The link-and-sync restore failed. Linking still succeeded, so callers may choose to continue. */
  data class Failed(val cause: Throwable? = null) : LinkAndSyncProgress

  /**
   * The primary asked this device to re-link, which invalidates the partial local registration. Callers must
   * wipe local data and restart rather than finishing registration.
   */
  data object RelinkRequired : LinkAndSyncProgress
}
