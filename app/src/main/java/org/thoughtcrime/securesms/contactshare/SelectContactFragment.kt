/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contactshare

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.paging.compose.collectAsLazyPagingItems
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.ComposeFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.contacts.index.ContactIndexRepository
import org.thoughtcrime.securesms.contactshare.screens.selectcontact.ContactIndexSource
import org.thoughtcrime.securesms.contactshare.screens.selectcontact.SelectContactAction
import org.thoughtcrime.securesms.contactshare.screens.selectcontact.SelectContactEvent
import org.thoughtcrime.securesms.contactshare.screens.selectcontact.SelectContactScreen
import org.thoughtcrime.securesms.contactshare.screens.selectcontact.SelectContactViewModel
import org.thoughtcrime.securesms.util.viewModel

/**
 * Lists the address book and Signal connections in one A-Z list.
 *
 * Unlike the system picker this does not need contacts permission to be useful, since Signal
 * connections come from our own database, so the prompt lives inside the screen rather than in front
 * of it.
 */
class SelectContactFragment : ComposeFragment() {

  companion object {
    fun create(): SelectContactFragment = SelectContactFragment()
  }

  /**
   * Owned by the view model, so the index survives the activity being recreated but is released with
   * the screen. Scoping it to the activity instead left a retained view model holding a repository
   * the destroyed activity had already closed.
   */
  private val viewModel: SelectContactViewModel by viewModel {
    SelectContactViewModel(
      source = ContactIndexSource(ContactIndexRepository(requireActivity().application)),
      savedState = it.createSavedStateHandle()
    )
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val rows = viewModel.rows.collectAsLazyPagingItems()

    val systemContactPicker = rememberLauncherForActivityResult(PickPhoneNumber) { uri: Uri? ->
      viewModel.onEvent(SelectContactEvent.SystemContactPicked(uri))
    }

    CollectActions(viewModel.actions) { action -> handleAction(action, systemContactPicker) }

    viewModel.contactsPermission.Content()

    SelectContactScreen(
      state = state,
      rows = rows,
      onEvent = viewModel::onEvent
    )
  }

  private fun handleAction(action: SelectContactAction, systemContactPicker: ActivityResultLauncher<Unit>) {
    when (action) {
      SelectContactAction.Exit -> requireActivity().onBackPressedDispatcher.onBackPressed()

      SelectContactAction.CouldNotOpenContact -> {
        Toast.makeText(requireContext(), R.string.SelectContactScreen__couldnt_open_contact, Toast.LENGTH_SHORT).show()
      }

      is SelectContactAction.ContactResolved -> {
        val intent = Intent().putExtra(SelectContactActivity.KEY_SOURCE, action.source)

        requireActivity().setResult(Activity.RESULT_OK, intent)
        requireActivity().finish()
      }

      SelectContactAction.LaunchSystemContactPicker -> systemContactPicker.launch(Unit)
    }
  }
}

/** Picks limited contact data, since we do not have contacts permission. */
private object PickPhoneNumber : ActivityResultContract<Unit, Uri?>() {
  override fun createIntent(context: Context, input: Unit): Intent {
    return Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
  }

  override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
    return if (resultCode == Activity.RESULT_OK) intent?.data else null
  }
}
