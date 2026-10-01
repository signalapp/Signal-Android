/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contactshare

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.compose.LocalChatColorProvider
import org.signal.core.util.getParcelableCompat
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.contactshare.screens.editname.ContactNameParts
import org.thoughtcrime.securesms.contactshare.screens.editname.EditContactNameEvent
import org.thoughtcrime.securesms.contactshare.screens.editname.EditContactNameResult
import org.thoughtcrime.securesms.contactshare.screens.editname.EditContactNameScreen
import org.thoughtcrime.securesms.contactshare.screens.editname.EditContactNameViewModel
import org.thoughtcrime.securesms.contactshare.screens.share.ShareContactAction
import org.thoughtcrime.securesms.contactshare.screens.share.ShareContactEvent
import org.thoughtcrime.securesms.contactshare.screens.share.ShareContactRepository
import org.thoughtcrime.securesms.contactshare.screens.share.ShareContactScreen
import org.thoughtcrime.securesms.contactshare.screens.share.ShareContactViewModel
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.rememberRecipientField
import org.thoughtcrime.securesms.util.viewModel

/** Lets the sender pick which parts of a contact to share. */
class ContactShareEditFragment : ComposeFragment() {

  companion object {
    private val TAG = Log.tag(ContactShareEditFragment::class)

    private const val ARG_SOURCE = "source"
    private const val ARG_RECIPIENT_ID = "recipient_id"

    fun create(source: SharedContactSource, recipientId: RecipientId): ContactShareEditFragment {
      return ContactShareEditFragment().apply {
        arguments = Bundle().apply {
          putParcelable(ARG_SOURCE, source)
          putParcelable(ARG_RECIPIENT_ID, recipientId)
        }
      }
    }
  }

  private val recipientId: RecipientId?
    get() = arguments?.getParcelableCompat(ARG_RECIPIENT_ID, RecipientId::class.java)

  private val editorViewModel: EditContactNameViewModel by viewModels()

  private val viewModel: ShareContactViewModel by viewModel {
    ShareContactViewModel(
      contactSource = arguments?.getParcelableCompat(ARG_SOURCE, SharedContactSource::class.java),
      recipientId = recipientId,
      repository = ShareContactRepository(),
      savedState = it.createSavedStateHandle()
    )
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editingName: ContactNameParts? by rememberSaveable { mutableStateOf(null) }

    CollectActions(viewModel.actions) { action -> handleAction(action) { editingName = it } }

    BackHandler(enabled = editingName != null) {
      editorViewModel.onEvent(EditContactNameEvent.BackClicked)
    }

    val editorState by editorViewModel.state.collectAsStateWithLifecycle()

    CollectActions(editorViewModel.results) { result ->
      if (result is EditContactNameResult.Saved) {
        viewModel.onEvent(ShareContactEvent.NameEdited(result.parts))
      }
      editingName = null
    }

    CompositionLocalProvider(
      LocalChatColorProvider provides { id ->
        rememberRecipientField(RecipientId.from(id)) {
          Color(chatColors.asSingleColor())
        }
      }
    ) {
      if (editingName == null) {
        ShareContactScreen(state = state, onEvent = viewModel::onEvent)
      } else {
        EditContactNameScreen(state = editorState, onEvent = editorViewModel::onEvent)
      }
    }
  }

  private fun handleAction(action: ShareContactAction, onEditName: (ContactNameParts?) -> Unit) {
    when (action) {
      ShareContactAction.Exit -> requireActivity().onBackPressedDispatcher.onBackPressed()

      ShareContactAction.InvalidContact -> {
        Toast.makeText(requireContext(), R.string.ContactShareEditActivity_invalid_contact, Toast.LENGTH_SHORT).show()
        requireActivity().finish()
      }

      is ShareContactAction.EditName -> {
        editorViewModel.onEvent(EditContactNameEvent.Initialize(action.parts))
        onEditName(action.parts)
      }

      is ShareContactAction.Send -> {
        val intent = Intent().putExtra(ContactShareEditActivityV2.KEY_CONTACTS, arrayListOf(action.contact))

        requireActivity().setResult(Activity.RESULT_OK, intent)
        requireActivity().finish()
      }
    }
  }
}
