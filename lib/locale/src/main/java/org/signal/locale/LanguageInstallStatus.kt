/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.locale

import com.google.android.play.core.splitinstall.model.SplitInstallErrorCode
import org.signal.core.util.ByteSize

sealed interface LanguageInstallStatus {
  /** [totalBytes] is the size of the download, or 0 while Play hasn't reported it. */
  data class Downloading(val totalBytes: ByteSize) : LanguageInstallStatus

  data class NeedsPlayConfirmation(val sessionId: Int) : LanguageInstallStatus

  data object Installed : LanguageInstallStatus

  data object Cancelled : LanguageInstallStatus

  data class Failed(val playErrorCode: Int) : LanguageInstallStatus {
    /** Play will never serve this install, so asking again cannot help. */
    val isPermanent: Boolean
      get() = playErrorCode == SplitInstallErrorCode.PLAY_STORE_NOT_FOUND ||
        playErrorCode == SplitInstallErrorCode.API_NOT_AVAILABLE ||
        playErrorCode == SplitInstallErrorCode.APP_NOT_OWNED

    val isStorageFull: Boolean
      get() = playErrorCode == SplitInstallErrorCode.INSUFFICIENT_STORAGE
  }
}
