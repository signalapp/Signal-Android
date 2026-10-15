/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.ui.incommon

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.ui.viewModel
import org.signal.core.util.getParcelableExtraCompat
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.CommunicationActions

/**
 * Displays a list of groups that the user has in common with the specified [RecipientId].
 */
class GroupsInCommonActivity : PassphraseRequiredActivity() {
  companion object {
    private const val EXTRA_RECIPIENT_ID = "recipient_id"

    fun createIntent(
      context: Context,
      recipientId: RecipientId
    ): Intent {
      return Intent(context, GroupsInCommonActivity::class.java).apply {
        putExtra(EXTRA_RECIPIENT_ID, recipientId)
      }
    }
  }

  private val viewModel by viewModel {
    GroupsInCommonViewModel(
      context = AppDependencies.application,
      recipientId = intent.getParcelableExtraCompat(EXTRA_RECIPIENT_ID, RecipientId::class.java)!!
    )
  }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    super.onCreate(savedInstanceState, ready)

    setContent {
      val state by viewModel.state.collectAsStateWithLifecycle()

      CollectActions(viewModel.actions) { action -> handleAction(action) }

      SignalTheme {
        GroupsInCommonScreen(
          state = state,
          onEvent = viewModel::onEvent
        )
      }
    }
  }

  private fun handleAction(action: GroupsInCommonAction) {
    when (action) {
      is GroupsInCommonAction.OpenGroupConversation -> navigateToConversation(action.group)
      GroupsInCommonAction.NavigateBack -> supportFinishAfterTransition()
    }
  }

  override fun finish() {
    super.finish()
    overridePendingTransition(0, R.anim.slide_fade_to_bottom)
  }

  private fun navigateToConversation(group: Recipient) {
    CommunicationActions.startConversation(this, group, null)
    finish()
  }
}
