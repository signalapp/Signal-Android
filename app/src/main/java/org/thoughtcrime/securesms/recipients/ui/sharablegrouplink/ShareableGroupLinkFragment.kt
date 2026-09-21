package org.thoughtcrime.securesms.recipients.ui.sharablegrouplink

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.signal.chatsettings.screens.sharablegrouplink.ShareableGroupLinkScreen
import org.signal.core.ui.compose.ComposeFragment
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.util.viewModel

/**
 * Fragment wrapping [ShareableGroupLinkScreen] to let a group's members share its link and its admins manage it.
 */
class ShareableGroupLinkFragment : ComposeFragment() {

  private val groupId: GroupId.V2
    get() = ShareableGroupLinkFragmentArgs.fromBundle(requireArguments()).groupId.requireV2()

  private val viewModel: ShareableGroupLinkViewModel by viewModel {
    ShareableGroupLinkViewModel(groupId)
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ShareableGroupLinkScreen(
      state = state,
      onEvent = viewModel::onEvent,
      onShareClick = {
        GroupLinkBottomSheetDialogFragment.show(childFragmentManager, groupId)
      },
      onNavigationClick = {
        requireActivity().onBackPressedDispatcher.onBackPressed()
      }
    )
  }
}
