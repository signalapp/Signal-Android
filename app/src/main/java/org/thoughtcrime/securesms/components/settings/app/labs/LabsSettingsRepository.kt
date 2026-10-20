/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.labs

import org.signal.appsettings.labs.LabsSettingsState
import org.thoughtcrime.securesms.keyvalue.SignalStore

/**
 * Reads and writes the labs feature flags behind [LabsSettingsViewModel].
 */
object LabsSettingsRepository {

  fun getState(): LabsSettingsState {
    return LabsSettingsState(
      individualChatPlaintextExport = SignalStore.labs.individualChatPlaintextExport,
      storyArchive = SignalStore.labs.storyArchive,
      incognito = SignalStore.labs.incognito,
      betterSearch = SignalStore.labs.betterSearch,
      starredMessages = SignalStore.labs.starredMessages,
      stickerReplies = SignalStore.labs.stickerReplies,
      improvedMessageDeletion = SignalStore.labs.improvedMessageDeletion
    )
  }

  fun setIndividualChatPlaintextExport(enabled: Boolean) {
    SignalStore.labs.individualChatPlaintextExport = enabled
  }

  fun setStoryArchive(enabled: Boolean) {
    SignalStore.labs.storyArchive = enabled
  }

  fun setIncognito(enabled: Boolean) {
    SignalStore.labs.incognito = enabled
  }

  fun setBetterSearch(enabled: Boolean) {
    SignalStore.labs.betterSearch = enabled
  }

  fun setStarredMessages(enabled: Boolean) {
    SignalStore.labs.starredMessages = enabled
  }

  fun setStickerReplies(enabled: Boolean) {
    SignalStore.labs.stickerReplies = enabled
  }

  fun setImprovedMessageDeletion(enabled: Boolean) {
    SignalStore.labs.improvedMessageDeletion = enabled
  }
}
