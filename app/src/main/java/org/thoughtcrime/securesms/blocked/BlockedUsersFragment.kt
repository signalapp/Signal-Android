/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.blocked

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.navigation.Navigation
import com.google.android.material.snackbar.Snackbar
import org.signal.core.ui.Rows.TextAndLabel
import org.signal.core.ui.Rows.TextRow
import org.signal.core.ui.Scaffolds
import org.thoughtcrime.securesms.BlockUnblockDialog
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.AvatarImage
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.ViewModelFactory
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import kotlin.jvm.optionals.getOrNull

class BlockedUsersFragment : ComposeFragment() {
  private val viewModel: BlockedUsersViewModel by activityViewModels(
    factoryProducer = ViewModelFactory.factoryProducer {
      BlockedUsersViewModel(BlockedUsersRepository(requireContext()))
    }
  )

  @Composable
  override fun FragmentContent() {
    Scaffolds.Settings(
      title = stringResource(R.string.BlockedUsersActivity__blocked_users),
      navigationIconPainter = painterResource(R.drawable.symbol_arrow_left_24),
      onNavigationClick = { requireActivity().onNavigateUp() }
    ) { paddingValues ->
      Column(
        Modifier
          .padding(paddingValues)
          .fillMaxSize()){
        BlockedUsers(Modifier.fillMaxSize())
      }
    }
  }

  @Composable
  fun BlockedUsers(modifier: Modifier = Modifier){
    val blockedUsers by viewModel.blockedUsers.collectAsStateWithLifecycle()

    val displaySnackbar : (Int, String) -> Unit = { messageResId, displayName ->
      Snackbar.make(requireView(), getString(messageResId, displayName), Snackbar.LENGTH_SHORT).show()
    }

    LaunchedEffect(Unit) {
      viewModel.events.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).collect {event->
        when(event) {
          is BlockUserEvent.BlockFailed -> displaySnackbar(R.string.BlockedUsersActivity__failed_to_block_s, event.recipient.getDisplayName(requireContext()))
          is BlockUserEvent.BlockSucceeded -> displaySnackbar(R.string.BlockedUsersActivity__s_has_been_blocked, event.recipient.getDisplayName(requireContext()))
          is BlockUserEvent.CreateAndBlockSucceeded -> displaySnackbar(R.string.BlockedUsersActivity__s_has_been_blocked, event.number)
          is BlockUserEvent.UnblockSucceeded -> displaySnackbar(R.string.BlockedUsersActivity__s_has_been_unblocked, event.recipient.getDisplayName(requireContext()))
        }
      }
    }

    Column(modifier){
      TextRow(
        text = stringResource(id = R.string.BlockedUsersActivity__add_blocked_user),
        label = stringResource(id = R.string.BlockedUsersActivity__blocked_users_will),
        onClick = { Navigation.findNavController(requireView())
          .safeNavigate(R.id.action_blockedUsersFragment_to_blockedUsersContactSelectionFragment) },
      )

      Text(
        text = stringResource(id = R.string.BlockedUsersActivity__blocked_users),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
          .padding(top = 8.dp, bottom = 12.dp)
          .padding(horizontal = dimensionResource(R.dimen.dsl_settings_gutter)),
      )

      if(blockedUsers.isEmpty()) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
          Spacer(Modifier.height(20.dp))
          Text(
            text = stringResource(id = R.string.BlockedUsersActivity__no_blocked_users),
            style = MaterialTheme.typography.bodyMedium,
          )
        }
      } else {
        LazyColumn {
          items(blockedUsers) { recipient ->
            TextRow(
              text = {
                TextAndLabel(
                  text = recipient.getDisplayName(requireContext()),
                  label = recipient.username.getOrNull(),
                )
              },
              icon = {
                Column {
                  AvatarImage(recipient = recipient, modifier = Modifier.size(dimensionResource(R.dimen.small_avatar_size)))
                }
              },
              onClick = { handleRecipientClicked(recipient) },
              paddingValues = PaddingValues(
                horizontal = dimensionResource(R.dimen.dsl_settings_gutter),
                vertical = dimensionResource(R.dimen.small_avatar_text_row_spacer_size)),
              spacerModifier = Modifier.padding(dimensionResource(R.dimen.small_avatar_text_row_spacer_size))
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

}