/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.exportaccountdata

import android.net.Uri

/**
 * One-shot side effects that have to be carried out by the host rather than the screen itself.
 */
sealed interface ExportAccountDataAction {
  /** Opens a browser link. */
  data class OpenBrowserLink(val url: String) : ExportAccountDataAction

  /** Open system share sheet so the user can pick where the generated report goes. */
  data class ShareGeneratedReport(val uri: Uri, val mimeType: String) : ExportAccountDataAction

  /** Leave the screen. */
  data object NavigateBack : ExportAccountDataAction
}
