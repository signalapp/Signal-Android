/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.util.ViewUtil
import org.signal.core.ui.viewModel
import org.thoughtcrime.securesms.mediakeyboard.SignalEmojiPicker
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
      emojiPicker = { onEmojiSelected -> SignalEmojiPicker(onEmojiSelected) }
    )
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
