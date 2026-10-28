/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.managestorage

import org.junit.Test
import org.signal.appsettings.managestorage.ManageStorageSettingsState.Dialog
import org.signal.appsettings.managestorage.ManageStorageSettingsState.OnDeviceStorageOptimizationState
import org.signal.core.models.storage.KeepMessagesDuration
import org.signal.core.ui.SignalLayoutStressTest

class ManageStorageSettingsLayoutTest : SignalLayoutStressTest() {

  private val populated = ManageStorageSettingsState(
    storageUsage = StorageUsage(
      photos = 123_456_789_012,
      videos = 987_654_321_098,
      files = 12_345_678_901,
      audio = 1_234_567_890,
      messages = 98_765_432_109,
      stickers = 123_456_789,
      other = 45_678_901_234
    ),
    onDeviceStorageOptimizationState = OnDeviceStorageOptimizationState.ENABLED,
    keepMessagesDuration = KeepMessagesDuration.SIX_MONTHS,
    lengthLimit = 123_456,
    syncTrimDeletes = true,
    isMultiDevice = true
  )

  @Test
  fun `calculating`() = assertPassesLayoutChecks {
    ManageStorageSettingsScreen(state = ManageStorageSettingsState(), onEvent = {})
  }

  @Test
  fun `populated`() = assertPassesLayoutChecks {
    ManageStorageSettingsScreen(state = populated, onEvent = {})
  }

  @Test
  fun `linked device`() = assertPassesLayoutChecks {
    ManageStorageSettingsScreen(state = populated.copy(onDeviceStorageOptimizationState = OnDeviceStorageOptimizationState.FEATURE_NOT_AVAILABLE, isPrimaryDevice = false), onEvent = {})
  }

  @Test
  fun `confirming delete message history`() = assertPassesLayoutChecks {
    ManageStorageSettingsScreen(state = populated.copy(dialog = Dialog.ConfirmDeleteChatHistory), onEvent = {})
  }

  @Test
  fun `reconfirming delete message history`() = assertPassesLayoutChecks {
    ManageStorageSettingsScreen(state = populated.copy(dialog = Dialog.ReconfirmDeleteChatHistory), onEvent = {})
  }

  @Test
  fun `paid tier pending`() = assertPassesLayoutChecks {
    ManageStorageSettingsScreen(state = populated.copy(onDeviceStorageOptimizationState = OnDeviceStorageOptimizationState.DISABLED, dialog = Dialog.PaidTierPending), onEvent = {})
  }

  @Test
  fun `confirming optimize storage with a local backup`() = assertPassesLayoutChecks {
    ManageStorageSettingsScreen(state = populated.copy(onDeviceStorageOptimizationState = OnDeviceStorageOptimizationState.DISABLED, dialog = Dialog.ConfirmOptimizeStorageWithLocalBackup), onEvent = {})
  }
}
