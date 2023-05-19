/**
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.calls.links.details

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ShareCompat
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.signal.core.ui.Dividers
import org.signal.core.ui.Rows
import org.signal.core.ui.Scaffolds
import org.signal.core.ui.theme.SignalTheme
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.ringrtc.CallLinkState.Restrictions
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.calls.links.CallLinks
import org.thoughtcrime.securesms.calls.links.EditCallLinkNameDialogFragment
import org.thoughtcrime.securesms.calls.links.SignalCallRow
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.conversation.colors.AvatarColor
import org.thoughtcrime.securesms.database.CallLinkTable
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.service.webrtc.links.CallLinkCredentials
import org.thoughtcrime.securesms.service.webrtc.links.SignalCallLinkState
import java.time.Instant

/**
 * Provides detailed info about a call link and allows the owner of that link
 * to modify call properties.
 */
class CallLinkDetailsFragment : ComposeFragment(), CallLinkDetailsCallback {

  private val viewModel: CallLinkDetailsViewModel by viewModels()
  private val lifecycleDisposable = LifecycleDisposable()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    lifecycleDisposable.bindTo(viewLifecycleOwner)
    parentFragmentManager.setFragmentResultListener(EditCallLinkNameDialogFragment.RESULT_KEY, viewLifecycleOwner) { resultKey, bundle ->
      if (bundle.containsKey(resultKey)) {
        setName(bundle.getString(resultKey)!!)
      }
    }
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state

    CallLinkDetails(
      state,
      this
    )
  }

  override fun onNavigationClicked() {
    findNavController().popBackStack()
  }

  override fun onJoinClicked() {
    // TODO("Not yet implemented")
  }

  override fun onEditNameClicked() {
    val name = viewModel.nameSnapshot
    findNavController().navigate(
      CallLinkDetailsFragmentDirections.actionCallLinkDetailsFragmentToEditCallLinkNameDialogFragment(name)
    )
  }

  override fun onShareClicked() {
    val mimeType = Intent.normalizeMimeType("text/plain")
    val shareIntent = ShareCompat.IntentBuilder(requireContext())
      .setText(CallLinks.url(viewModel.rootKeySnapshot))
      .setType(mimeType)
      .createChooserIntent()

    try {
      startActivity(shareIntent)
    } catch (e: ActivityNotFoundException) {
      Toast.makeText(requireContext(), R.string.CreateCallLinkBottomSheetDialogFragment__failed_to_open_share_sheet, Toast.LENGTH_LONG).show()
    }
  }

  override fun onDeleteClicked() {
    lifecycleDisposable += viewModel.revoke().subscribeBy {
    }
  }

  override fun onApproveAllMembersChanged(checked: Boolean) {
    lifecycleDisposable += viewModel.setApproveAllMembers(checked).subscribeBy {
    }
  }

  private fun setName(name: String) {
    lifecycleDisposable += viewModel.setName(name).subscribeBy {
    }
  }
}

private interface CallLinkDetailsCallback {
  fun onNavigationClicked()
  fun onJoinClicked()
  fun onEditNameClicked()
  fun onShareClicked()
  fun onDeleteClicked()
  fun onApproveAllMembersChanged(checked: Boolean)
}

@Preview
@Composable
private fun CallLinkDetailsPreview() {
  val avatarColor = remember {
    AvatarColor.random()
  }

  val callLink = remember {
    val credentials = CallLinkCredentials.generate()
    CallLinkTable.CallLink(
      recipientId = RecipientId.UNKNOWN,
      roomId = credentials.roomId,
      credentials = credentials,
      state = SignalCallLinkState(
        name = "Call Name",
        revoked = false,
        restrictions = Restrictions.NONE,
        expiration = Instant.MAX
      ),
      avatarColor = avatarColor
    )
  }

  SignalTheme(false) {
    CallLinkDetails(
      CallLinkDetailsState(
        callLink
      ),
      object : CallLinkDetailsCallback {
        override fun onNavigationClicked() = Unit
        override fun onJoinClicked() = Unit
        override fun onEditNameClicked() = Unit
        override fun onShareClicked() = Unit
        override fun onDeleteClicked() = Unit
        override fun onApproveAllMembersChanged(checked: Boolean) = Unit
      }
    )
  }
}

@Composable
private fun CallLinkDetails(
  state: CallLinkDetailsState,
  callback: CallLinkDetailsCallback
) {
  Scaffolds.Settings(
    title = stringResource(id = R.string.CallLinkDetailsFragment__call_details),
    onNavigationClick = callback::onNavigationClicked,
    navigationIconPainter = painterResource(id = R.drawable.ic_arrow_left_24)
  ) { paddingValues ->
    if (state.callLink == null) {
      return@Settings
    }

    Column(modifier = Modifier.padding(paddingValues)) {
      SignalCallRow(
        callLink = state.callLink,
        onJoinClicked = callback::onJoinClicked,
        modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
      )

      Rows.TextRow(
        text = stringResource(id = R.string.CallLinkDetailsFragment__add_call_name),
        modifier = Modifier.clickable(onClick = callback::onEditNameClicked)
      )

      Rows.ToggleRow(
        checked = state.callLink.state.restrictions == Restrictions.ADMIN_APPROVAL,
        text = stringResource(id = R.string.CallLinkDetailsFragment__approve_all_members),
        onCheckChanged = callback::onApproveAllMembersChanged
      )

      Dividers.Default()

      Rows.TextRow(
        text = stringResource(id = R.string.CallLinkDetailsFragment__share_link),
        icon = ImageVector.vectorResource(id = R.drawable.symbol_link_24),
        modifier = Modifier.clickable(onClick = callback::onShareClicked)
      )

      Rows.TextRow(
        text = stringResource(id = R.string.CallLinkDetailsFragment__delete_call_link),
        icon = ImageVector.vectorResource(id = R.drawable.symbol_trash_24),
        foregroundTint = MaterialTheme.colorScheme.error,
        modifier = Modifier.clickable(onClick = callback::onDeleteClicked)
      )
    }
  }
}
