/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.chats

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import org.signal.appsettings.R
import org.signal.appsettings.backups.BackupCreationProgress
import org.signal.appsettings.backups.BackupCreationProgressRow
import org.signal.core.ui.biometrics.rememberBiometricsAuthentication
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.compose.Snackbars
import org.signal.core.ui.compose.Texts

@VisibleForTesting
object ChatsSettingsTestTags {
  const val SCROLLER = "scroller"
  const val ROW_GENERATE_LINK_PREVIEWS = "row-generate-link-previews"
  const val ROW_AUTOPLAY_STICKERS_AND_GIFS = "row-autoplay-stickers-and-gifs"
  const val ROW_USE_ADDRESS_BOOK = "row-use-address-book"
  const val ROW_KEEP_MUTED_CHATS_ARCHIVED = "row-keep-muted-chats-archived"
  const val ROW_CHAT_FOLDERS = "row-chat-folders"
  const val ROW_EXPORT_CHAT_HISTORY = "row-export-chat-history"
  const val ROW_EXPORT_PROGRESS = "row-export-progress"
  const val ROW_USE_SYSTEM_EMOJI = "row-use-system-emoji"
  const val ROW_ENTER_KEY_SENDS = "row-enter-key-sends"
  const val DIALOG_CHOOSE_A_FOLDER = "dialog-choose-a-folder"
  const val DIALOG_EXPORT_COMPLETE = "dialog-export-complete"
}

@Composable
fun ChatsSettingsScreen(
  state: ChatsSettingsState,
  onEvent: (ChatsSettingsEvents) -> Unit
) {
  LifecycleResumeEffect(Unit) {
    onEvent(ChatsSettingsEvents.Refresh)
    onPauseOrDispose {}
  }

  val coroutineScope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }
  val authenticationFailedMessage = stringResource(R.string.ChatsSettingsFragment__authentication_failed)
  val exportBiometrics = rememberBiometricsAuthentication(
    promptTitle = stringResource(R.string.ChatsSettingsFragment__unlock_to_export_chat_history),
    onAuthenticationFailed = {
      coroutineScope.launch {
        snackbarHostState.showSnackbar(authenticationFailedMessage)
      }
    }
  )

  val backPressedDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

  Scaffolds.Settings(
    title = stringResource(R.string.preferences_chats__chats),
    onNavigationClick = { backPressedDispatcher?.onBackPressed() },
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    snackbarHost = {
      Snackbars.Host(snackbarHostState)
    }
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .padding(paddingValues)
        .testTag(ChatsSettingsTestTags.SCROLLER)
    ) {
      item {
        Rows.ToggleRow(
          modifier = Modifier.testTag(ChatsSettingsTestTags.ROW_GENERATE_LINK_PREVIEWS),
          text = stringResource(R.string.preferences__generate_link_previews),
          label = stringResource(R.string.preferences__retrieve_link_previews_from_websites_for_messages),
          enabled = state.isRegisteredAndUpToDate(),
          checked = state.generateLinkPreviews,
          onCheckChanged = { onEvent(ChatsSettingsEvents.GenerateLinkPreviewsChanged(it)) }
        )
      }

      item {
        Rows.ToggleRow(
          modifier = Modifier.testTag(ChatsSettingsTestTags.ROW_AUTOPLAY_STICKERS_AND_GIFS),
          text = stringResource(R.string.ChatsSettingsFragment__autoplay_stickers_and_gifs),
          label = stringResource(R.string.ChatsSettingsFragment__when_turned_on_stickers_and_gifs_will_play_automatically),
          enabled = state.isRegisteredAndUpToDate(),
          checked = state.shouldAutoplayStickersAndGifs,
          onCheckChanged = { onEvent(ChatsSettingsEvents.AutoplayStickersAndGifsChanged(it)) }
        )
      }

      item {
        Rows.ToggleRow(
          modifier = Modifier.testTag(ChatsSettingsTestTags.ROW_USE_ADDRESS_BOOK),
          text = stringResource(R.string.preferences__pref_use_address_book_photos),
          label = stringResource(R.string.preferences__display_contact_photos_from_your_address_book_if_available),
          enabled = state.isRegisteredAndUpToDate(),
          checked = state.useAddressBook,
          onCheckChanged = { onEvent(ChatsSettingsEvents.UseAddressBookChanged(it)) }
        )
      }

      item {
        Rows.ToggleRow(
          modifier = Modifier.testTag(ChatsSettingsTestTags.ROW_KEEP_MUTED_CHATS_ARCHIVED),
          text = stringResource(R.string.preferences__pref_keep_muted_chats_archived),
          label = stringResource(R.string.preferences__muted_chats_that_are_archived_will_remain_archived),
          enabled = state.isRegisteredAndUpToDate(),
          checked = state.keepMutedChatsArchived,
          onCheckChanged = { onEvent(ChatsSettingsEvents.KeepMutedChatsArchivedChanged(it)) }
        )
      }

      item {
        Dividers.Default()
      }

      item {
        Texts.SectionHeader(stringResource(R.string.ChatsSettingsFragment__chat_folders))
      }

      if (state.folderCount == 1) {
        item {
          Rows.TextRow(
            modifier = Modifier.testTag(ChatsSettingsTestTags.ROW_CHAT_FOLDERS),
            text = stringResource(R.string.ChatsSettingsFragment__add_chat_folder),
            enabled = state.isRegisteredAndUpToDate(),
            onClick = { onEvent(ChatsSettingsEvents.ChatFoldersClicked) }
          )
        }
      } else {
        item {
          Rows.TextRow(
            modifier = Modifier.testTag(ChatsSettingsTestTags.ROW_CHAT_FOLDERS),
            text = stringResource(R.string.ChatsSettingsFragment__add_edit_chat_folder),
            label = pluralStringResource(R.plurals.ChatsSettingsFragment__d_folder, state.folderCount, state.folderCount),
            enabled = state.isRegisteredAndUpToDate(),
            onClick = { onEvent(ChatsSettingsEvents.ChatFoldersClicked) }
          )
        }
      }

      if (state.isPlaintextExportEnabled) {
        item {
          Dividers.Default()
        }

        if (state.plaintextExportProgress.isIdle) {
          item(key = "export_chat_history_row") {
            Rows.TextRow(
              modifier = Modifier
                .animateItem()
                .testTag(ChatsSettingsTestTags.ROW_EXPORT_CHAT_HISTORY),
              text = stringResource(R.string.ChatsSettingsFragment__export_chat_history),
              label = stringResource(R.string.ChatsSettingsFragment__export_chat_history_label),
              onClick = {
                exportBiometrics.withBiometricsAuthentication {
                  onEvent(ChatsSettingsEvents.ExportChatHistoryAuthenticated)
                }
              }
            )
          }
        } else {
          item(key = "export_chat_history_progress") {
            BackupCreationProgressRow(
              modifier = Modifier
                .animateItem()
                .testTag(ChatsSettingsTestTags.ROW_EXPORT_PROGRESS),
              progress = state.plaintextExportProgress,
              isRemote = false,
              onCancel = { onEvent(ChatsSettingsEvents.CancelInFlightExportClicked) }
            )
          }
        }
      }

      item {
        Dividers.Default()
      }

      item {
        Texts.SectionHeader(stringResource(R.string.ChatsSettingsFragment__keyboard))
      }

      item {
        Rows.ToggleRow(
          modifier = Modifier.testTag(ChatsSettingsTestTags.ROW_USE_SYSTEM_EMOJI),
          text = stringResource(R.string.preferences_advanced__use_system_emoji),
          enabled = state.isRegisteredAndUpToDate(),
          checked = state.useSystemEmoji,
          onCheckChanged = { onEvent(ChatsSettingsEvents.UseSystemEmojiChanged(it)) }
        )
      }

      item {
        Rows.ToggleRow(
          modifier = Modifier.testTag(ChatsSettingsTestTags.ROW_ENTER_KEY_SENDS),
          text = stringResource(R.string.ChatsSettingsFragment__send_with_enter),
          enabled = state.isRegisteredAndUpToDate(),
          checked = state.enterKeySends,
          onCheckChanged = { onEvent(ChatsSettingsEvents.EnterKeySendsChanged(it)) }
        )
      }
    }
  }

  if (state.isPlaintextExportEnabled) {
    ChatExportDialogs(
      state = state.chatExportState,
      onEvent = onEvent
    )
  }
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun ChatsSettingsScreenPreview() {
  ChatsSettingsScreen(
    state = ChatsSettingsState(
      generateLinkPreviews = true,
      useAddressBook = true,
      keepMutedChatsArchived = true,
      useSystemEmoji = false,
      enterKeySends = false,
      localBackupsEnabled = true,
      folderCount = 1,
      userUnregistered = false,
      clientDeprecated = false,
      isPlaintextExportEnabled = true,
      plaintextExportProgress = BackupCreationProgress.Idle
    ),
    onEvent = {}
  )
}
