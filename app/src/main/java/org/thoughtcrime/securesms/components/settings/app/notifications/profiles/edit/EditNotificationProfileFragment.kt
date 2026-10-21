/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.fragment.findNavController
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.compose.rememberViewModelStoreOwner
import org.signal.core.ui.util.ViewUtil
import org.signal.core.ui.viewModel
import org.signal.mediakeyboard.screens.emojisheet.EmojiSheet
import org.signal.mediakeyboard.screens.emojisheet.EmojiSheetAction
import org.thoughtcrime.securesms.components.emoji.RecentEmojiPageModel
import org.thoughtcrime.securesms.mediakeyboard.SignalEmojiSheetRepository
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Dual use Edit/Create notification profile fragment. Use to create in the create profile flow,
 * and then to edit from profile details. Responsible for naming and emoji.
 */
class EditNotificationProfileFragment : ComposeFragment() {

  private val viewModel: EditNotificationProfileViewModel by viewModel {
    EditNotificationProfileViewModel(EditNotificationProfileFragmentArgs.fromBundle(requireArguments()).profileId)
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectActions(viewModel.actions) { handleAction(it) }

    EditNotificationProfileScreen(
      state = state,
      onEvent = viewModel::onEvent,
      emojiPicker = { onEmojiSelected -> EmojiPicker(onEmojiSelected) }
    )
  }

  @Composable
  private fun EmojiPicker(onEmojiSelected: (String) -> Unit) {
    val context = LocalContext.current
    val repository = remember(context) { SignalEmojiSheetRepository(context.applicationContext, RecentEmojiPageModel.RECENT_STORAGE_KEY, messageId = null) }

    // A fresh picker each time the sheet opens, rather than one left mid-search from the last time.
    CompositionLocalProvider(LocalViewModelStoreOwner provides rememberViewModelStoreOwner()) {
      EmojiSheet(
        repository = repository,
        onAction = { action ->
          if (action is EmojiSheetAction.EmojiSelected) {
            onEmojiSelected(action.emoji)
          }
        }
      )
    }
  }

  private fun handleAction(action: EditNotificationProfileAction) {
    ViewUtil.hideKeyboard(requireContext(), requireView())

    when (action) {
      EditNotificationProfileAction.NavigateBack -> findNavController().navigateUp()
      is EditNotificationProfileAction.NavigateToAllowedMembers -> {
        findNavController().safeNavigate(EditNotificationProfileFragmentDirections.actionEditNotificationProfileFragmentToAddAllowedMembersFragment(action.profileId))
      }
    }
  }
}
