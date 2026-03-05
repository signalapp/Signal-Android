/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.restore.local

data class SelectableBackup(
  val timestamp: Long,
  val backupTime: String,
  val backupSize: String
)
