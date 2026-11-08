/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.ui.creategroup.details

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import org.signal.core.models.media.Media
import org.signal.core.models.recipients.RecipientId
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.viewModel
import org.signal.core.util.getParcelableCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.picker.AvatarPickerFragment
import org.thoughtcrime.securesms.components.settings.app.privacy.expire.ExpireTimerSettingsFragment
import org.thoughtcrime.securesms.conversation.ConversationIntents
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.ui.disappearingmessages.RecipientDisappearingMessagesActivity
import org.thoughtcrime.securesms.util.fragments.requireListener
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Fragment for adding group details like name or avatar
 */
class AddGroupDetailsFragment : ComposeFragment() {

  private val viewModel: AddGroupDetailsViewModel by viewModel {
    AddGroupDetailsViewModel(
      recipientIds = AddGroupDetailsFragmentArgs.fromBundle(requireArguments()).recipientIds.toList()
    )
  }

  private lateinit var callback: Callback

  private val disappearingMessagesLauncher = registerForActivityResult(StartActivityForResult()) { result ->
    val data = result.data
    if (result.resultCode == Activity.RESULT_OK && data != null && data.hasExtra(ExpireTimerSettingsFragment.FOR_RESULT_VALUE)) {
      viewModel.onEvent(AddGroupDetailsEvent.DisappearingMessagesTimerSelected(data.getIntExtra(ExpireTimerSettingsFragment.FOR_RESULT_VALUE, 0)))
    }
  }

  override fun onAttach(context: Context) {
    super.onAttach(context)
    callback = requireListener()
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    parentFragmentManager.setFragmentResultListener(AvatarPickerFragment.REQUEST_KEY_SELECT_AVATAR, this) { _, bundle ->
      val media = bundle.getParcelableCompat(AvatarPickerFragment.SELECT_AVATAR_MEDIA, Media::class.java)
      when {
        bundle.getBoolean(AvatarPickerFragment.SELECT_AVATAR_CLEAR) -> viewModel.onEvent(AddGroupDetailsEvent.AvatarCleared)
        media != null -> viewModel.onEvent(AddGroupDetailsEvent.AvatarSelected(media))
      }
    }
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectActions(viewModel.actions) { action -> handleAction(action) }

    AddGroupDetailsScreen(
      state = state,
      onEvent = viewModel::onEvent
    )
  }

  private fun handleAction(action: AddGroupDetailsAction) {
    when (action) {
      AddGroupDetailsAction.NavigateBack -> {
        callback.onNavigationButtonPressed()
      }

      is AddGroupDetailsAction.NavigateToAvatarPicker -> {
        findNavController().safeNavigate(
          AddGroupDetailsFragmentDirections.actionAddGroupDetailsFragmentToAvatarPicker(null, action.currentMedia).setIsNewGroup(true)
        )
      }

      is AddGroupDetailsAction.LaunchDisappearingMessagesPicker -> {
        disappearingMessagesLauncher.launch(RecipientDisappearingMessagesActivity.forCreateGroup(requireContext(), action.timer))
      }

      is AddGroupDetailsAction.OpenConversation -> {
        startActivity(ConversationIntents.createBuilderSync(requireContext(), action.groupId, action.threadId).build())
        requireActivity().finish()
      }

      is AddGroupDetailsAction.GroupCreated -> {
        callback.onGroupCreated(action.groupId, action.threadId, action.invitedMembers)
      }

      AddGroupDetailsAction.ShowTryAgainLater -> {
        Toast.makeText(requireContext(), R.string.AddGroupDetailsFragment__try_again_later, Toast.LENGTH_SHORT).show()
      }

      AddGroupDetailsAction.ShowGroupCreationFailed -> {
        Toast.makeText(requireContext(), R.string.AddGroupDetailsFragment__group_creation_failed, Toast.LENGTH_SHORT).show()
      }
    }
  }

  interface Callback {
    fun onGroupCreated(recipientId: RecipientId, threadId: Long, invitedMembers: List<Recipient>)

    fun onNavigationButtonPressed()
  }
}
