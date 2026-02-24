/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.status

/**
 * Renderable state for [ArchiveUploadStatusBannerView].
 * Each state carries only the data it needs; rendering logic lives in the composable.
 */
sealed interface ArchiveUploadStatusBannerViewState {
  /** Creating the intiial backup file. */
  data object CreatingBackupFile : ArchiveUploadStatusBannerViewState

  /** Actively uploading media with progress. */
  data class Uploading(
    val completedSize: String,
    val totalSize: String,
    val progress: Float
  ) : ArchiveUploadStatusBannerViewState

  /** Restore paused because Wi-Fi is required. */
  data object PausedMissingWifi : ArchiveUploadStatusBannerViewState

  /** Restore paused because there is no internet connection. */
  data object PausedNoInternet : ArchiveUploadStatusBannerViewState

  /** Restore completed successfully. */
  data class Finished(
    val uploadedSize: String
  ) : ArchiveUploadStatusBannerViewState
}
