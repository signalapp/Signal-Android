package org.thoughtcrime.securesms.calls.links.create

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ShareCompat
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import org.signal.core.ui.Buttons
import org.signal.core.ui.Dividers
import org.signal.core.ui.Rows
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.calls.links.CallLinks
import org.thoughtcrime.securesms.calls.links.EditCallLinkNameDialogFragment
import org.thoughtcrime.securesms.calls.links.SignalCallRow
import org.thoughtcrime.securesms.compose.ComposeBottomSheetDialogFragment
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragment
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragmentArgs
import org.thoughtcrime.securesms.database.CallLinkTable
import org.thoughtcrime.securesms.sharing.MultiShareArgs
import org.thoughtcrime.securesms.util.Util

/**
 * Bottom sheet for creating call links
 */
class CreateCallLinkBottomSheetDialogFragment : ComposeBottomSheetDialogFragment() {

  private val viewModel: CreateCallLinkViewModel by viewModels()

  override val peekHeightPercentage: Float = 1f

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    parentFragmentManager.setFragmentResultListener(EditCallLinkNameDialogFragment.RESULT_KEY, viewLifecycleOwner) { resultKey, bundle ->
      if (bundle.containsKey(resultKey)) {
        viewModel.setCallName(bundle.getString(resultKey)!!)
      }
    }
  }

  @Composable
  override fun SheetContent() {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentSize(Alignment.Center)
    ) {
      val callLink: CallLinkTable.CallLink by viewModel.callLink

      Handle(modifier = Modifier.align(Alignment.CenterHorizontally))

      Spacer(modifier = Modifier.height(20.dp))

      Text(
        text = stringResource(id = R.string.CreateCallLinkBottomSheetDialogFragment__create_call_link),
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
      )

      Spacer(modifier = Modifier.height(24.dp))

      SignalCallRow(
        callLink = callLink,
        onJoinClicked = this@CreateCallLinkBottomSheetDialogFragment::onJoinClicked
      )

      Spacer(modifier = Modifier.height(12.dp))

      Rows.TextRow(
        text = stringResource(id = R.string.CreateCallLinkBottomSheetDialogFragment__add_call_name),
        modifier = Modifier.clickable(onClick = this@CreateCallLinkBottomSheetDialogFragment::onAddACallNameClicked)
      )

      Rows.ToggleRow(
        checked = callLink.isApprovalRequired,
        text = stringResource(id = R.string.CreateCallLinkBottomSheetDialogFragment__approve_all_members),
        onCheckChanged = viewModel::setApproveAllMembers,
        modifier = Modifier.clickable(onClick = viewModel::toggleApproveAllMembers)
      )

      Dividers.Default()

      Rows.TextRow(
        text = stringResource(id = R.string.CreateCallLinkBottomSheetDialogFragment__share_link_via_signal),
        icon = ImageVector.vectorResource(id = R.drawable.symbol_forward_24),
        modifier = Modifier.clickable(onClick = this@CreateCallLinkBottomSheetDialogFragment::onShareViaSignalClicked)
      )

      Rows.TextRow(
        text = stringResource(id = R.string.CreateCallLinkBottomSheetDialogFragment__copy_link),
        icon = ImageVector.vectorResource(id = R.drawable.symbol_copy_android_24),
        modifier = Modifier.clickable(onClick = this@CreateCallLinkBottomSheetDialogFragment::onCopyLinkClicked)
      )

      Rows.TextRow(
        text = stringResource(id = R.string.CreateCallLinkBottomSheetDialogFragment__share_link),
        icon = ImageVector.vectorResource(id = R.drawable.symbol_share_android_24),
        modifier = Modifier.clickable(onClick = this@CreateCallLinkBottomSheetDialogFragment::onShareLinkClicked)
      )

      Buttons.MediumTonal(
        onClick = this@CreateCallLinkBottomSheetDialogFragment::onDoneClicked,
        modifier = Modifier
          .padding(end = dimensionResource(id = R.dimen.core_ui__gutter))
          .align(Alignment.End)
      ) {
        Text(text = stringResource(id = R.string.CreateCallLinkBottomSheetDialogFragment__done))
      }

      Spacer(modifier = Modifier.size(16.dp))
    }
  }

  private fun onAddACallNameClicked() {
    val snapshot = viewModel.callLink.value
    findNavController().navigate(
      CreateCallLinkBottomSheetDialogFragmentDirections.actionCreateCallLinkBottomSheetToEditCallLinkNameDialogFragment(snapshot.name)
    )
  }

  private fun onJoinClicked() {
  }

  private fun onDoneClicked() {
  }

  private fun onShareViaSignalClicked() {
    val snapshot = viewModel.callLink.value

    MultiselectForwardFragment.showFullScreen(
      childFragmentManager,
      MultiselectForwardFragmentArgs(
        canSendToNonPush = false,
        multiShareArgs = listOf(
          MultiShareArgs.Builder()
            .withDraftText(snapshot.identifier)
            .build()
        )
      )
    )
  }

  private fun onCopyLinkClicked() {
    val snapshot = viewModel.callLink.value
    Util.copyToClipboard(requireContext(), CallLinks.url(snapshot.identifier))
    Toast.makeText(requireContext(), R.string.CreateCallLinkBottomSheetDialogFragment__copied_to_clipboard, Toast.LENGTH_LONG).show()
  }

  private fun onShareLinkClicked() {
    val snapshot = viewModel.callLink.value
    val mimeType = Intent.normalizeMimeType("text/plain")
    val shareIntent = ShareCompat.IntentBuilder(requireContext())
      .setText(CallLinks.url(snapshot.identifier))
      .setType(mimeType)
      .createChooserIntent()
      .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    try {
      startActivity(shareIntent)
    } catch (e: ActivityNotFoundException) {
      Toast.makeText(requireContext(), R.string.CreateCallLinkBottomSheetDialogFragment__failed_to_open_share_sheet, Toast.LENGTH_LONG).show()
    }
  }
}
