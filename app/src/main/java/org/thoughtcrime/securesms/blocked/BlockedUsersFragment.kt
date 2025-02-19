/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.blocked

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.signal.core.ui.Rows.TextAndLabel
import org.signal.core.ui.Rows.TextRow
import org.thoughtcrime.securesms.BlockUnblockDialog
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.AvatarImage
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.recipients.Recipient
import kotlin.jvm.optionals.getOrNull

class BlockedUsersFragment : ComposeFragment() {
  private val viewModel: BlockedUsersViewModel by activityViewModels()

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun FragmentContent() {
    val rippleConfiguration =
      RippleConfiguration(color = MaterialTheme.colorScheme.onSurface,
        rippleAlpha = RippleAlpha(.1F, .1F, .1F, .1F)
      )

    CompositionLocalProvider(LocalRippleConfiguration provides rippleConfiguration) {
      BlockedUsersScreen(Modifier)
    }
  }

  @Composable
  fun BlockedUsersScreen(modifier: Modifier){
    val blockedUsers = viewModel.recipients.collectAsStateWithLifecycle()

    Column(modifier){
      TextRow(
        text = stringResource(id = R.string.BlockedUsersActivity__add_blocked_user),
        label = stringResource(id = R.string.BlockedUsersActivity__blocked_users_will),
        onClick = { (context as? Listener)?.handleAddUserToBlockedList() },
      )

      Text(
        text = stringResource(id = R.string.BlockedUsersActivity__blocked_users),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
          .padding(top = 8.dp, bottom = 12.dp)
          .padding(horizontal = 24.dp),
        color = MaterialTheme.colorScheme.onSurface
      )

      if(blockedUsers.value.isEmpty()) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
          Spacer(Modifier.height(20.dp))
          Text(
            text = stringResource(id = R.string.BlockedUsersActivity__no_blocked_users),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
          )
        }
      } else {
        LazyColumn {
          items(blockedUsers.value) { recipient ->
            TextRow(
              text = {
                TextAndLabel(
                  text = recipient.getDisplayName(requireContext()),
                  label = recipient.username.getOrNull(),
                )
              },
              icon = {
                Column {
                  AvatarImage(recipient = recipient, modifier = Modifier.size(48.dp))
                }
              },
              onClick = { handleRecipientClicked(recipient) },
              paddingValues = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
              spacerModifier = Modifier.padding(8.dp)
            )
          }
        }
      }
    }
  }

  private fun handleRecipientClicked(recipient: Recipient) {
    BlockUnblockDialog.showUnblockFor(requireContext(), viewLifecycleOwner.lifecycle, recipient) {
      viewModel.unblock(recipient.id)
    }
  }

  fun interface Listener {
    fun handleAddUserToBlockedList()
  }

}