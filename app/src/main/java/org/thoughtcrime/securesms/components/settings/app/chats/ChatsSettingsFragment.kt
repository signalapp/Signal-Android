package org.thoughtcrime.securesms.components.settings.app.chats

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalPreview
import org.signal.core.ui.compose.Texts
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.compose.rememberStatusBarColorNestedScrollModifier
import org.thoughtcrime.securesms.util.RemoteConfig
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Displays a list of chats settings options to the user, including
 * generating link previews and keeping muted chats archived.
 */
class ChatsSettingsFragment : ComposeFragment() {

  private val viewModel: ChatsSettingsViewModel by viewModels()

  override fun onResume() {
    super.onResume()
    viewModel.refresh()
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val callbacks = remember { Callbacks() }

    ChatsSettingsScreen(
      state = state,
      callbacks = callbacks,
      isRemoteBackupsAvailable = RemoteConfig.messageBackups
    )
  }

  private inner class Callbacks : ChatsSettingsCallbacks {
    override fun onNavigationClick() {
      requireActivity().onBackPressedDispatcher.onBackPressed()
    }

    override fun onGenerateLinkPreviewsChanged(enabled: Boolean) {
      viewModel.setGenerateLinkPreviewsEnabled(enabled)
    }

    override fun onUseAddressBookChanged(enabled: Boolean) {
      viewModel.setUseAddressBook(enabled)
    }

    override fun onKeepMutedChatsArchivedChanged(enabled: Boolean) {
      viewModel.setKeepMutedChatsArchived(enabled)
    }

    override fun onAddAChatFolderClick() {
      findNavController().safeNavigate(R.id.action_chatsSettingsFragment_to_chatFoldersFragment)
    }

    override fun onAddOrEditFoldersClick() {
      findNavController().safeNavigate(R.id.action_chatsSettingsFragment_to_chatFoldersFragment)
    }

    override fun onUseSystemEmojiChanged(enabled: Boolean) {
      viewModel.setUseSystemEmoji(enabled)
    }

    override fun onEnterKeySendsChanged(enabled: Boolean) {
      viewModel.setEnterKeySends(enabled)
    }

    override fun onChatBackupsClick() {
      findNavController().safeNavigate(R.id.action_chatsSettingsFragment_to_backupsPreferenceFragment)
    }
  }
}

private interface ChatsSettingsCallbacks {
  fun onNavigationClick() = Unit
  fun onGenerateLinkPreviewsChanged(enabled: Boolean) = Unit
  fun onUseAddressBookChanged(enabled: Boolean) = Unit
  fun onKeepMutedChatsArchivedChanged(enabled: Boolean) = Unit
  fun onAddAChatFolderClick() = Unit
  fun onAddOrEditFoldersClick() = Unit
  fun onUseSystemEmojiChanged(enabled: Boolean) = Unit
  fun onEnterKeySendsChanged(enabled: Boolean) = Unit
  fun onChatBackupsClick() = Unit

  object Empty : ChatsSettingsCallbacks
}

@Composable
private fun ChatsSettingsScreen(
  isRemoteBackupsAvailable: Boolean,
  state: ChatsSettingsState,
  callbacks: ChatsSettingsCallbacks
) {
  Scaffolds.Settings(
    title = stringResource(R.string.preferences_chats__chats),
    onNavigationClick = callbacks::onNavigationClick,
    navigationIcon = ImageVector.vectorResource(R.drawable.symbol_arrow_start_24)
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .padding(paddingValues)
        .then(rememberStatusBarColorNestedScrollModifier())
    ) {
      item {
        Rows.ToggleRow(
          text = stringResource(R.string.preferences__generate_link_previews),
          label = stringResource(R.string.preferences__retrieve_link_previews_from_websites_for_messages),
          enabled = state.isRegisteredAndUpToDate(),
          checked = state.generateLinkPreviews,
          onCheckChanged = callbacks::onGenerateLinkPreviewsChanged
        )
      }

      item {
        Rows.ToggleRow(
          text = stringResource(R.string.preferences__pref_use_address_book_photos),
          label = stringResource(R.string.preferences__display_contact_photos_from_your_address_book_if_available),
          enabled = state.isRegisteredAndUpToDate(),
          checked = state.useAddressBook,
          onCheckChanged = callbacks::onUseAddressBookChanged
        )
      }

      item {
        Rows.ToggleRow(
          text = stringResource(R.string.preferences__pref_keep_muted_chats_archived),
          label = stringResource(R.string.preferences__muted_chats_that_are_archived_will_remain_archived),
          enabled = state.isRegisteredAndUpToDate(),
          checked = state.keepMutedChatsArchived,
          onCheckChanged = callbacks::onKeepMutedChatsArchivedChanged
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
            text = stringResource(R.string.ChatsSettingsFragment__add_chat_folder),
            enabled = state.isRegisteredAndUpToDate(),
            onClick = callbacks::onAddAChatFolderClick
          )
        }
      } else {
        item {
          Rows.TextRow(
            text = stringResource(R.string.ChatsSettingsFragment__add_edit_chat_folder),
            label = pluralStringResource(R.plurals.ChatsSettingsFragment__d_folder, state.folderCount, state.folderCount),
            enabled = state.isRegisteredAndUpToDate(),
            onClick = callbacks::onAddOrEditFoldersClick
          )
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
          text = stringResource(R.string.preferences_advanced__use_system_emoji),
          enabled = state.isRegisteredAndUpToDate(),
          checked = state.useSystemEmoji,
          onCheckChanged = callbacks::onUseSystemEmojiChanged
        )
      }

      item {
        Rows.ToggleRow(
          text = stringResource(R.string.ChatsSettingsFragment__send_with_enter),
          enabled = state.isRegisteredAndUpToDate(),
          checked = state.enterKeySends,
          onCheckChanged = callbacks::onEnterKeySendsChanged
        )
      }

      if (!isRemoteBackupsAvailable) {
        item {
          Dividers.Default()
        }

        item {
          Texts.SectionHeader(stringResource(R.string.preferences_chats__backups))
        }

        item {
          Rows.TextRow(
            text = stringResource(R.string.preferences_chats__chat_backups),
            label = stringResource(if (state.localBackupsEnabled) R.string.arrays__enabled else R.string.arrays__disabled),
            enabled = state.localBackupsEnabled || state.isRegisteredAndUpToDate(),
            onClick = callbacks::onChatBackupsClick
          )
        }
      }
    }
  }
}

@SignalPreview
@Composable
private fun ChatsSettingsScreenPreview() {
  Previews.Preview {
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
        clientDeprecated = false
      ),
      callbacks = ChatsSettingsCallbacks.Empty,
      isRemoteBackupsAvailable = false
    )
  }
}
