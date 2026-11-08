/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.dataandstorage

import org.junit.Test
import org.signal.core.models.calls.CallDataMode
import org.signal.core.models.media.SentMediaQuality
import org.signal.core.ui.SignalLayoutStressTest

class DataAndStorageSettingsLayoutTest : SignalLayoutStressTest() {

  private val populated = DataAndStorageSettingsState(
    totalStorageUseBytes = 987_654_321_000,
    autoDownload = AutoDownloadNetwork.entries.associateWith { AutoDownloadMediaType.entries.toSet() },
    alwaysAutoDownloadedBytes = 102_400,
    sentMediaQuality = SentMediaQuality.HIGH,
    callDataMode = CallDataMode.LOW_ALWAYS,
    forceWebsocketMode = true,
    isProxyEnabled = true
  )

  @Test
  fun `calculating`() = assertPassesLayoutChecks {
    DataAndStorageSettingsScreen(state = DataAndStorageSettingsState(), onEvent = {})
  }

  @Test
  fun `populated`() = assertPassesLayoutChecks {
    DataAndStorageSettingsScreen(state = populated, onEvent = {})
  }

  @Test
  fun `without play services`() = assertPassesLayoutChecks {
    DataAndStorageSettingsScreen(state = populated.copy(forceWebsocketMode = false, playServicesAvailable = false), onEvent = {})
  }

  @Test
  fun `confirming stay connected in background`() = assertPassesLayoutChecks {
    DataAndStorageSettingsScreen(state = populated.copy(dialog = DataAndStorageSettingsState.Dialog.ConfirmStayConnectedInBackground), onEvent = {})
  }
}
