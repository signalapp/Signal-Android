/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.shakereport

/**
 * One-shot side effects that the hosting fragment has to carry out.
 */
sealed interface ShakeToReportAction {

  /** Close the dialog. */
  data object Dismiss : ShakeToReportAction

  /** Copy [url] to the clipboard and close the dialog. */
  data class CopyUrl(val url: String) : ShakeToReportAction

  /** Open the share sheet for [url] and close the dialog. */
  data class ShareUrl(val url: String) : ShakeToReportAction
}
