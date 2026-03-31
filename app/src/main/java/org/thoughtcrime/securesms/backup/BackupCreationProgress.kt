/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup

import org.thoughtcrime.securesms.keyvalue.protos.LocalBackupCreationProgress

val LocalBackupCreationProgress.isIdle: Boolean
  get() = idle != null || succeeded != null || failed != null || canceled != null || (exporting == null && transferring == null)

fun LocalBackupCreationProgress.exportProgress(): Float {
  val exporting = exporting ?: return 0f
  return if (exporting.frameTotalCount == 0L) 0f else exporting.frameExportCount / exporting.frameTotalCount.toFloat()
}

fun LocalBackupCreationProgress.transferProgress(): Float {
  val transferring = transferring ?: return 0f
  return if (transferring.total == 0L) 0f else transferring.completed / transferring.total.toFloat()
}
