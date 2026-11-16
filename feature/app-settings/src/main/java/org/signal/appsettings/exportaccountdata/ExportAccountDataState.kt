/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.exportaccountdata

/**
 * Everything [ExportAccountDataScreen] needs to render.
 */
data class ExportAccountDataState(
  val format: ExportAccountDataFormat = ExportAccountDataFormat.TXT,
  val dialog: Dialog = Dialog.None
) {
  /** Whichever dialog the screen is showing, if any. Only one is ever up at a time. */
  sealed interface Dialog {
    data object None : Dialog

    /** Asks the user to confirm the export and warns them to only share with trusted people or apps. */
    data object ConfirmExport : Dialog

    /** Blocking dialog while the report is being generated. */
    data object GeneratingReport : Dialog

    /** Report couldn't be generated. */
    data object ReportGenerationFailed : Dialog
  }
}
