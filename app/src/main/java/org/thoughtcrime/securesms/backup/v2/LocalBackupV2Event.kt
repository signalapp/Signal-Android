/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2

class LocalBackupV2Event(val type: Type, val count: Long = 0, val estimatedTotalCount: Long = 0) {
  enum class Type {
    PROGRESS_ACCOUNT,
    PROGRESS_RECIPIENT,
    PROGRESS_THREAD,
    PROGRESS_CALL,
    PROGRESS_STICKER,
    PROGRESS_MESSAGE,
    PROGRESS_ATTACHMENT,
    PROGRESS_VERIFYING,
    FINISHED
  }
}
