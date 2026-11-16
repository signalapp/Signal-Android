/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.shakereport

/**
 * Reminder that these events are logged, so don't include anything sensitive in the toString.
 */
sealed interface ShakeToReportEvent {

  /** The user tapped submit on the initial prompt. */
  data object SubmitClicked : ShakeToReportEvent

  /** The user tapped retry after a failed submission. */
  data object RetryClicked : ShakeToReportEvent

  /** The user tapped copy on the uploaded log's URL. */
  data object CopyClicked : ShakeToReportEvent

  /** The user tapped share on the uploaded log's URL. */
  data object ShareClicked : ShakeToReportEvent

  /** The user tapped cancel or close, pressed back, or tapped outside the dialog. */
  data object DismissRequested : ShakeToReportEvent
}
