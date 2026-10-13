/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.chats

import org.signal.appsettings.backups.BackupCreationProgress

data class ChatsSettingsState(
  val generateLinkPreviews: Boolean,
  val mirrorRecordedVideos: Boolean = false,
  val useAddressBook: Boolean,
  val keepMutedChatsArchived: Boolean,
  val useSystemEmoji: Boolean,
  val enterKeySends: Boolean,
  val localBackupsEnabled: Boolean,
  val folderCount: Int,
  val userUnregistered: Boolean,
  val clientDeprecated: Boolean,
  val isPlaintextExportEnabled: Boolean,
  val plaintextExportProgress: BackupCreationProgress = BackupCreationProgress.Idle,
  val chatExportState: ChatExportState = ChatExportState.None,
  val includeMediaInExport: Boolean = false,
  val shouldAutoplayStickersAndGifs: Boolean = false,
  val selectedAiModel: String = "openai/gpt-oss-120b",
  val availableAiModels: List<String> = emptyList(),
  val showAiModelSelectionDialog: Boolean = false
) {
  fun isRegisteredAndUpToDate(): Boolean {
    return !userUnregistered && !clientDeprecated
  }
}
