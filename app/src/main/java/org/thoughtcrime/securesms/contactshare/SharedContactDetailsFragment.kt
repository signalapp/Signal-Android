/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contactshare

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.util.concurrent.SignalDispatchers
import org.signal.core.util.logging.Log
import org.signal.core.util.requireParcelableCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.calls.YouAreAlreadyInACallSnackbar
import org.thoughtcrime.securesms.contactshare.screens.details.SharedContactDetailsAction
import org.thoughtcrime.securesms.contactshare.screens.details.SharedContactDetailsRepository
import org.thoughtcrime.securesms.contactshare.screens.details.SharedContactDetailsScreen
import org.thoughtcrime.securesms.contactshare.screens.details.SharedContactDetailsViewModel
import org.thoughtcrime.securesms.conversation.v2.AddToContactsContract
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.groups.ui.addtogroup.AddToGroupsActivity
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.viewModel

/**
 * Shows what a sender chose to share on a received contact card.
 */
class SharedContactDetailsFragment : ComposeFragment() {

  companion object {
    private val TAG = Log.tag(SharedContactDetailsFragment::class)

    private const val ARG_CONTACT = "contact"

    fun create(contact: Contact): SharedContactDetailsFragment {
      return SharedContactDetailsFragment().apply {
        arguments = Bundle().apply {
          putParcelable(ARG_CONTACT, contact)
        }
      }
    }
  }

  private val contact: Contact
    get() = requireArguments().requireParcelableCompat(ARG_CONTACT, Contact::class.java)

  private val addToContactsLauncher = registerForActivityResult(AddToContactsContract()) {}

  private val viewModel: SharedContactDetailsViewModel by viewModel {
    SharedContactDetailsViewModel(
      contact = contact,
      repository = SharedContactDetailsRepository()
    )
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectActions(viewModel.actions, ::handleAction)

    SharedContactDetailsScreen(state = state, onEvent = viewModel::onEvent)
  }

  private fun handleAction(action: SharedContactDetailsAction) {
    when (action) {
      SharedContactDetailsAction.Exit -> requireActivity().onBackPressedDispatcher.onBackPressed()

      is SharedContactDetailsAction.CopyToClipboard -> {
        requireContext().getSystemService<ClipboardManager>()?.setPrimaryClip(ClipData.newPlainText(null, action.text))
        toast(R.string.SharedContactDetailsScreen__copied)
      }

      is SharedContactDetailsAction.OpenInMaps -> {
        // The card carries a formatted address rather than coordinates, so this is a geo query.
        launchIntent(
          intent = Intent(Intent.ACTION_VIEW, "geo:0,0?q=${Uri.encode(action.address)}".toUri()),
          missingAppMessage = R.string.SharedContactDetailsScreen__couldnt_open_maps
        )
      }

      SharedContactDetailsAction.AddToPhoneContacts -> addToPhoneContacts()

      is SharedContactDetailsAction.InviteBySms -> {
        launchIntent(
          intent = Intent(Intent.ACTION_SENDTO, "smsto:${action.number}".toUri()).putExtra("sms_body", inviteText()),
          missingAppMessage = null
        )
      }

      is SharedContactDetailsAction.InviteByEmail -> {
        CommunicationActions.openEmail(
          requireContext(),
          action.email,
          getString(R.string.SharedContactDetailsScreen__join_me_on_signal),
          inviteText()
        )
      }

      is SharedContactDetailsAction.StartChat -> {
        CommunicationActions.startConversation(requireContext(), Recipient.resolved(action.recipientId), null)
      }

      is SharedContactDetailsAction.StartVideoCall -> {
        CommunicationActions.startVideoCall(this, Recipient.resolved(action.recipientId)) {
          YouAreAlreadyInACallSnackbar.show(requireView())
        }
      }

      is SharedContactDetailsAction.StartAudioCall -> {
        CommunicationActions.startVoiceCall(this, Recipient.resolved(action.recipientId)) {
          YouAreAlreadyInACallSnackbar.show(requireView())
        }
      }

      is SharedContactDetailsAction.AddToGroup -> addToGroup(action.recipientId)
    }
  }

  private fun addToGroup(recipientId: RecipientId) {
    lifecycleScope.launch {
      val existingGroups = withContext(SignalDispatchers.IO) {
        SignalDatabase.groups.getPushGroupsContainingMember(recipientId).map { it.recipientId }
      }

      launchIntent(
        intent = AddToGroupsActivity.createIntent(requireContext(), recipientId, existingGroups),
        missingAppMessage = null
      )
    }
  }

  private fun addToPhoneContacts() {
    val contact = contact

    lifecycleScope.launch {
      // Building the intent reads and recompresses the avatar, so it cannot run on the main thread.
      val intent = withContext(SignalDispatchers.IO) { ContactUtil.buildAddToContactsIntent(requireContext(), contact) }

      launchIntent(
        intent = intent,
        missingAppMessage = R.string.SharedContactDetailsScreen__couldnt_open_contacts,
        start = addToContactsLauncher::launch
      )
    }
  }

  private fun launchIntent(intent: Intent, missingAppMessage: Int?, start: (Intent) -> Unit = ::startActivity) {
    try {
      start(intent)
    } catch (e: ActivityNotFoundException) {
      Log.w(TAG, "Nothing installed to handle ${intent.action}.", e)
      missingAppMessage?.let { toast(it) }
    }
  }

  private fun inviteText(): String = getString(R.string.InviteActivity_lets_switch_to_signal, getString(R.string.install_url))

  private fun toast(message: Int) = Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
}
