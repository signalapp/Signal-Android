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
import org.signal.core.ui.Previews
import org.signal.core.ui.Rows.TextAndLabel
import org.signal.core.ui.Rows.TextRow
import org.signal.core.ui.Scaffolds
import org.signal.core.ui.SignalPreview
import org.thoughtcrime.securesms.BlockUnblockDialog
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.AvatarImage
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.ViewModelFactory
import org.thoughtcrime.securesms.util.navigation.safeNavigate

class BlockedUsersFragment : ComposeFragment() {
  private val viewModel: BlockedUsersViewModel by activityViewModels(
    factoryProducer = ViewModelFactory.factoryProducer {
      BlockedUsersViewModel(BlockedUsersRepository(requireContext()))
    }
  )

  @Composable
  override fun FragmentContent() {
    val blockedUsers by viewModel.blockedUsers.collectAsStateWithLifecycle()
    BlockedUsersScreen(blockedUsers)
  }

  @Composable
  fun BlockedUsersScreen(blockedUsers :List<BlockedUserRecipientState>){
    Scaffolds.Settings(
      title = stringResource(R.string.BlockedUsersActivity__blocked_users),
      navigationIconPainter = painterResource(R.drawable.symbol_arrow_left_24),
      onNavigationClick = { requireActivity().onNavigateUp() }
    ) { paddingValues ->
      Column(
        Modifier
          .padding(paddingValues)
          .fillMaxSize()){
        BlockedUsersContent(blockedUsers)
      }
    }
  }

  @Composable
  fun BlockedUsersContent(blockedUsers :List<BlockedUserRecipientState>, modifier: Modifier = Modifier, ){
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
        LazyColumn(modifier.weight(1f)) {
          items(blockedUsers) { state ->
            TextRow(
              text = {
                TextAndLabel(
                  text = state.displayName,
                  label = state.username,
                )
              },
              icon = {
                Column {
                  AvatarImage(recipient = state.recipient, modifier = Modifier.size(dimensionResource(R.dimen.small_avatar_size)))
                }
              },
              onClick = { handleRecipientClicked(state.recipient) },
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

  @SignalPreview
  @Composable
  fun BlockedUsersPreview(){
    val previewRecipients = listOf(
      BlockedUserRecipientState(
        Recipient(),
        displayName = "Test User",
        username = "testuser.01"
      ),
      BlockedUserRecipientState(
        Recipient(),
        displayName = "Another Person",
      ),
      BlockedUserRecipientState(
        Recipient(),
        displayName = "Someone Else",
        username = "anonymous.01"
      ),

    )

    Previews.Preview(){
      BlockedUsersScreen(blockedUsers = previewRecipients)
    }

  }
}