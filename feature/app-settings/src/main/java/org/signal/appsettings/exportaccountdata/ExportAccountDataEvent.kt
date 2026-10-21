/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.exportaccountdata

/**
 * Reminder that these events are logged, so don't include anything sensitive in the toString.
 */
sealed interface ExportAccountDataEvent {

  /** The user tapped the learn more link in the explanation. */
  data class LearnMoreClicked(val url: String) : ExportAccountDataEvent

  /** The user picked the format they want the report in. */
  data class FormatSelected(val format: ExportAccountDataFormat) : ExportAccountDataEvent

  /** The user tapped the export button. */
  data object ExportReportClicked : ExportAccountDataEvent

  /** The user confirmed they want to export the report. */
  data object ExportConfirmed : ExportAccountDataEvent

  /** Dismisses the current dialog. */
  data object DialogDismissed : ExportAccountDataEvent

  /** The user tapped the navigation (back) icon. */
  data object NavigateBackClicked : ExportAccountDataEvent
}
