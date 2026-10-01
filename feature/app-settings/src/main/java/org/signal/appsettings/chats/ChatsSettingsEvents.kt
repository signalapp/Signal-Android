/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.chats

import android.net.Uri
import org.signal.appsettings.backups.BackupCreationProgress

/**
 * Everything that can happen on the chats settings screen: the user's interactions, plus the plaintext
 * export progress changing underneath us.
 */
sealed interface ChatsSettingsEvents {

  /**
   * The screen became visible again, so the folder count and local backup state may be stale.
   */
  data object Refresh : ChatsSettingsEvents

  /**
   * User tapped the row that adds or edits chat folders.
   */
  data object ChatFoldersClicked : ChatsSettingsEvents

  /**
   * The in-flight plaintext export reported new progress.
   */
  data class PlaintextExportProgressChanged(val progress: BackupCreationProgress) : ChatsSettingsEvents

  data class GenerateLinkPreviewsChanged(val enabled: Boolean) : ChatsSettingsEvents

  data class AutoplayStickersAndGifsChanged(val enabled: Boolean) : ChatsSettingsEvents

  data class UseAddressBookChanged(val enabled: Boolean) : ChatsSettingsEvents

  data class KeepMutedChatsArchivedChanged(val enabled: Boolean) : ChatsSettingsEvents

  data class UseSystemEmojiChanged(val enabled: Boolean) : ChatsSettingsEvents

  data class EnterKeySendsChanged(val enabled: Boolean) : ChatsSettingsEvents

  /**
   * User got past their screen lock after asking to export their chat history.
   */
  data object ExportChatHistoryAuthenticated : ChatsSettingsEvents

  /**
   * User asked to stop an export that is already running.
   */
  data object CancelInFlightExportClicked : ChatsSettingsEvents

  /**
   * User chose whether the export should include media.
   */
  data class ExportConfirmed(val withMedia: Boolean) : ChatsSettingsEvents

  /**
   * User picked the folder to export into.
   */
  data class ExportFolderSelected(val uri: Uri) : ChatsSettingsEvents

  /**
   * User backed out of the export flow before it started.
   */
  data object StartExportCanceled : ChatsSettingsEvents

  /**
   * User acknowledged that the export finished.
   */
  data object ExportCompletionConfirmed : ChatsSettingsEvents
}
