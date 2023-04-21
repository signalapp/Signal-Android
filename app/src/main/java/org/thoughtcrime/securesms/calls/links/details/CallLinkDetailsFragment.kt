package org.thoughtcrime.securesms.calls.links.details

import android.os.Bundle
import android.view.View
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
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import org.signal.core.ui.Dividers
import org.signal.core.ui.Rows
import org.signal.core.ui.Scaffolds
import org.signal.core.ui.theme.SignalTheme
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.calls.links.EditCallLinkNameDialogFragment
import org.thoughtcrime.securesms.calls.links.SignalCallRow
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.conversation.colors.AvatarColor
import org.thoughtcrime.securesms.database.CallLinkTable

/**
 * Provides detailed info about a call link and allows the owner of that link
 * to modify call properties.
 */
class CallLinkDetailsFragment : ComposeFragment(), CallLinkDetailsCallback {

  private val viewModel: CallLinkViewModel by viewModels()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    parentFragmentManager.setFragmentResultListener(EditCallLinkNameDialogFragment.RESULT_KEY, viewLifecycleOwner) { resultKey, bundle ->
      if (bundle.containsKey(resultKey)) {
        viewModel.setName(bundle.getString(resultKey)!!)
      }
    }
  }

  @Composable
  override fun FragmentContent() {
    val isLoading by viewModel.isLoading
    val callLink by viewModel.callLink

    CallLinkDetails(
      isLoading,
      callLink,
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
    val name = viewModel.callLink.value.name
    findNavController().navigate(
      CallLinkDetailsFragmentDirections.actionCallLinkDetailsFragmentToEditCallLinkNameDialogFragment(name)
    )
  }

  override fun onShareClicked() {
    // TODO("Not yet implemented")
  }

  override fun onDeleteClicked() {
    // TODO("Not yet implemented")
  }

  override fun onApproveAllMembersChanged(checked: Boolean) {
    // TODO("Not yet implemented")
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
    CallLinkTable.CallLink(
      name = "Call Name",
      identifier = "call-id-1",
      isApprovalRequired = false,
      avatarColor = avatarColor
    )
  }

  SignalTheme(false) {
    CallLinkDetails(
      false,
      callLink,
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
  isLoading: Boolean,
  callLink: CallLinkTable.CallLink,
  callback: CallLinkDetailsCallback
) {
  Scaffolds.Settings(
    title = stringResource(id = R.string.CallLinkDetailsFragment__call_details),
    onNavigationClick = callback::onNavigationClicked,
    navigationIconPainter = painterResource(id = R.drawable.ic_arrow_left_24)
  ) { paddingValues ->
    if (isLoading) {
      return@Settings
    }

    Column(modifier = Modifier.padding(paddingValues)) {
      SignalCallRow(
        callLink = callLink,
        onJoinClicked = callback::onJoinClicked,
        modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
      )

      Rows.TextRow(
        text = stringResource(id = R.string.CallLinkDetailsFragment__add_call_name),
        modifier = Modifier.clickable(onClick = callback::onEditNameClicked)
      )

      Rows.ToggleRow(
        checked = callLink.isApprovalRequired,
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
