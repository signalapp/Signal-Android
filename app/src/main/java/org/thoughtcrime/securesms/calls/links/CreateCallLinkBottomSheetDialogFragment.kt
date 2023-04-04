package org.thoughtcrime.securesms.calls.links

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Alignment.Companion.End
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ShareCompat
import androidx.fragment.app.viewModels
import org.signal.core.ui.Buttons
import org.signal.core.ui.Dividers
import org.signal.core.ui.Rows
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeBottomSheetDialogFragment
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragment
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragmentArgs
import org.thoughtcrime.securesms.sharing.MultiShareArgs
import org.thoughtcrime.securesms.util.Util

/**
 * Bottom sheet for creating call links
 */
class CreateCallLinkBottomSheetDialogFragment : ComposeBottomSheetDialogFragment() {

  private val viewModel: CreateCallLinkViewModel by viewModels()

  override val peekHeightPercentage: Float = 1f

  @Composable
  override fun SheetContent() {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentSize(Alignment.Center)
    ) {
      val callName: String by viewModel.callName
      val callLink: String by viewModel.callLink
      val approveAllMembers: Boolean by viewModel.approveAllMembers

      Handle(modifier = Modifier.align(CenterHorizontally))

      Spacer(modifier = Modifier.height(20.dp))

      Text(
        text = stringResource(id = R.string.CreateCallLinkBottomSheetDialogFragment__create_call_link),
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
      )

      Spacer(modifier = Modifier.height(24.dp))

      SignalCallRow(
        callName = callName,
        callLink = callLink,
        onJoinClicked = this@CreateCallLinkBottomSheetDialogFragment::onJoinClicked
      )

      Spacer(modifier = Modifier.height(12.dp))

      Rows.TextRow(
        text = stringResource(id = R.string.CreateCallLinkBottomSheetDialogFragment__add_call_name),
        modifier = Modifier.clickable(onClick = this@CreateCallLinkBottomSheetDialogFragment::onAddACallNameClicked)
      )

      Rows.ToggleRow(
        checked = approveAllMembers,
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
          .align(End)
      ) {
        Text(text = stringResource(id = R.string.CreateCallLinkBottomSheetDialogFragment__done))
      }

      Spacer(modifier = Modifier.size(16.dp))
    }
  }

  private fun onAddACallNameClicked() {
    EditCallLinkNameDialogFragment().show(childFragmentManager, null)
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
            .withDraftText(snapshot)
            .build()
        )
      )
    )
  }

  private fun onCopyLinkClicked() {
    val snapshot = viewModel.callLink.value
    Util.copyToClipboard(requireContext(), snapshot)
    Toast.makeText(requireContext(), R.string.CreateCallLinkBottomSheetDialogFragment__copied_to_clipboard, Toast.LENGTH_LONG).show()
  }

  private fun onShareLinkClicked() {
    val snapshot = viewModel.callLink.value
    val mimeType = Intent.normalizeMimeType("text/plain")
    val shareIntent = ShareCompat.IntentBuilder(requireContext())
      .setText(snapshot)
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

@Composable
private fun SignalCallRow(
  callName: String,
  callLink: String,
  onJoinClicked: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = dimensionResource(id = R.dimen.core_ui__gutter))
      .border(
        width = 1.25.dp,
        color = MaterialTheme.colorScheme.outline,
        shape = RoundedCornerShape(18.dp)
      )
      .padding(16.dp)
  ) {
    Image(
      imageVector = ImageVector.vectorResource(id = R.drawable.symbol_video_display_bold_40),
      contentScale = ContentScale.Inside,
      contentDescription = null,
      colorFilter = ColorFilter.tint(Color(0xFF5151F6)),
      modifier = Modifier
        .size(64.dp)
        .background(
          color = Color(0xFFE5E5FE),
          shape = CircleShape
        )
    )

    Spacer(modifier = Modifier.width(10.dp))

    Column(
      modifier = Modifier
        .weight(1f)
        .align(CenterVertically)
    ) {
      Text(
        text = callName.ifEmpty { stringResource(id = R.string.CreateCallLinkBottomSheetDialogFragment__signal_call) }
      )
      Text(
        text = callLink,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }

    Spacer(modifier = Modifier.width(10.dp))

    Buttons.Small(
      onClick = onJoinClicked,
      modifier = Modifier.align(CenterVertically)
    ) {
      Text(text = stringResource(id = R.string.CreateCallLinkBottomSheetDialogFragment__join))
    }
  }
}
