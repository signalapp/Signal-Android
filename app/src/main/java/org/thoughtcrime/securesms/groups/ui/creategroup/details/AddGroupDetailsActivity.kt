/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.ui.creategroup.details

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.commit
import androidx.navigation.fragment.NavHostFragment
import org.signal.core.models.recipients.RecipientId
import org.signal.core.util.getParcelableArrayListExtraCompat
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.conversation.ConversationIntents
import org.thoughtcrime.securesms.groups.ui.managegroup.dialogs.GroupInviteSentDialog
import org.thoughtcrime.securesms.recipients.Recipient

/**
 * Activity to add group details like name or avatar when creating a group
 */
class AddGroupDetailsActivity : PassphraseRequiredActivity(), AddGroupDetailsFragment.Callback {

  companion object {
    private const val EXTRA_RECIPIENTS = "recipient_ids"

    @JvmStatic
    fun newIntent(context: Context, recipients: Collection<RecipientId>): Intent {
      return Intent(context, AddGroupDetailsActivity::class.java)
        .putParcelableArrayListExtra(EXTRA_RECIPIENTS, ArrayList(recipients))
    }
  }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    super.onCreate(savedInstanceState, ready)
    setContentView(R.layout.add_group_details_activity)

    if (savedInstanceState == null) {
      val recipientIds = requireNotNull(intent.getParcelableArrayListExtraCompat(EXTRA_RECIPIENTS, RecipientId::class.java))
      val arguments = AddGroupDetailsFragmentArgs.Builder(recipientIds.toTypedArray()).build()

      supportFragmentManager.commit {
        replace(R.id.nav_host_fragment, NavHostFragment.create(R.navigation.create_group, arguments.toBundle()))
      }
    }
  }

  override fun onGroupCreated(recipientId: RecipientId, threadId: Long, invitedMembers: List<Recipient>) {
    if (invitedMembers.isEmpty()) {
      goToConversation(recipientId, threadId)
    } else {
      supportFragmentManager.setFragmentResultListener(GroupInviteSentDialog.RESULT_DISMISSED, this) { _, _ ->
        goToConversation(recipientId, threadId)
      }
      GroupInviteSentDialog.show(supportFragmentManager, invitedMembers)
    }
  }

  override fun onNavigationButtonPressed() {
    setResult(RESULT_CANCELED)
    finish()
  }

  private fun goToConversation(recipientId: RecipientId, threadId: Long) {
    val intent = ConversationIntents.createBuilderSync(this, recipientId, threadId)
      .firstTimeInSelfCreatedGroup()
      .build()

    startActivity(intent)
    setResult(RESULT_OK)
    finish()
  }
}
