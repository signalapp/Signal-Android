/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.shakereport

import kotlinx.coroutines.withContext
import org.signal.core.util.concurrent.SignalDispatchers
import org.thoughtcrime.securesms.logsubmit.SubmitDebugLogRepository

class ShakeToReportRepository(
  private val submitDebugLogRepository: SubmitDebugLogRepository = SubmitDebugLogRepository()
) {

  suspend fun submitLog(): SubmitResult {
    return withContext(SignalDispatchers.IO) {
      submitDebugLogRepository
        .buildAndSubmitLogSync(System.currentTimeMillis())
        .map<SubmitResult> { SubmitResult.Success(it) }
        .orElse(SubmitResult.Failure)
    }
  }

  sealed interface SubmitResult {
    data class Success(val url: String) : SubmitResult
    data object Failure : SubmitResult
  }
}
