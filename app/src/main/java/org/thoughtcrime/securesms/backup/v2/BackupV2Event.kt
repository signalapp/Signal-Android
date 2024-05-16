/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2

class BackupV2Event(val type: Type, val count: Long, val estimatedTotalCount: Long) {
  enum class Type {
    PROGRESS_MESSAGES,
    PROGRESS_ATTACHMENTS,
    FINISHED
  }
}
