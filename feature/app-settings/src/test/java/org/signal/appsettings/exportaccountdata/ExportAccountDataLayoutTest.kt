/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.exportaccountdata

import org.junit.Test
import org.signal.appsettings.exportaccountdata.ExportAccountDataState.Dialog
import org.signal.core.ui.SignalLayoutStressTest

class ExportAccountDataLayoutTest : SignalLayoutStressTest() {

  @Test
  fun `txt selected`() = assertPassesLayoutChecks {
    ExportAccountDataScreen(state = ExportAccountDataState(format = ExportAccountDataFormat.TXT), onEvent = {})
  }

  @Test
  fun `json selected`() = assertPassesLayoutChecks {
    ExportAccountDataScreen(state = ExportAccountDataState(format = ExportAccountDataFormat.JSON), onEvent = {})
  }

  @Test
  fun `confirming the export`() = assertPassesLayoutChecks {
    ExportAccountDataScreen(state = ExportAccountDataState(dialog = Dialog.ConfirmExport), onEvent = {})
  }

  @Test
  fun `generating the report`() = assertPassesLayoutChecks {
    ExportAccountDataScreen(state = ExportAccountDataState(dialog = Dialog.GeneratingReport), onEvent = {})
  }

  @Test
  fun `report generation failed`() = assertPassesLayoutChecks {
    ExportAccountDataScreen(state = ExportAccountDataState(dialog = Dialog.ReportGenerationFailed), onEvent = {})
  }
}
