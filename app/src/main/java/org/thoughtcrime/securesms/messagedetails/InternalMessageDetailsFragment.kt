/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.messagedetails

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentActivity
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.ComposeFullScreenDialogFragment
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Dividers
import org.signal.core.util.Util
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.messagedetails.InternalMessageDetailsViewModel.AttachmentInfo
import org.thoughtcrime.securesms.messagedetails.InternalMessageDetailsViewModel.ViewState
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.ui.bottomsheet.RecipientBottomSheetDialogFragment
import org.thoughtcrime.securesms.util.viewModel

class InternalMessageDetailsFragment : ComposeFullScreenDialogFragment() {
  companion object {
    const val ARG_MESSAGE_ID = "message_id"

    @JvmStatic
    fun create(messageRecord: MessageRecord): InternalMessageDetailsFragment {
      return InternalMessageDetailsFragment().apply {
        arguments = bundleOf(
          ARG_MESSAGE_ID to messageRecord.id
        )
      }
    }
  }

  val viewModel: InternalMessageDetailsViewModel by viewModel { InternalMessageDetailsViewModel(requireArguments().getLong(ARG_MESSAGE_ID, 0)) }

  @Composable
  override fun DialogContent() {
    val state by viewModel.state
    val actionResult by viewModel.actionResult
    val context = LocalContext.current

    LaunchedEffect(actionResult) {
      actionResult?.let {
        Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        viewModel.consumeActionResult()
      }
    }

    state?.let {
      Content(
        state = it,
        onOffloadLocalData = viewModel::offloadLocalData,
        onDeleteFromCdn = viewModel::deleteFromCdn
      )
    }
  }
}

@Composable
private fun Content(
  state: ViewState,
  onOffloadLocalData: (Long) -> Unit = {},
  onDeleteFromCdn: (Long, Boolean) -> Unit = { _, _ -> }
) {
  val context = LocalContext.current

  Surface(
    modifier = Modifier
      .fillMaxSize()
  ) {
    Column(
      modifier = Modifier.verticalScroll(rememberScrollState())
    ) {
      Text(
        text = "Message Details",
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier
          .padding(8.dp)
          .fillMaxWidth()
      )
      ClickToCopyRow(
        name = "MessageId",
        value = state.id.toString()
      )
      ClickToCopyRow(
        name = "Sent Timestamp",
        value = state.sentTimestamp.toString()
      )
      ClickToCopyRow(
        name = "Received Timestamp",
        value = state.receivedTimestamp.toString()
      )

      val serverTimestampString = if (state.serverSentTimestamp <= 0L) {
        "N/A"
      } else {
        state.serverSentTimestamp.toString()
      }

      ClickToCopyRow(
        name = "Server Sent Timestamp",
        value = serverTimestampString
      )
      DetailRow(
        name = "To",
        value = state.to.toString(),
        onClick = {
          val fragmentManager = (context as FragmentActivity).supportFragmentManager
          RecipientBottomSheetDialogFragment.show(fragmentManager, state.to, null)
        }
      )
      DetailRow(
        name = "From",
        value = state.from.toString(),
        onClick = {
          val fragmentManager = (context as FragmentActivity).supportFragmentManager
          RecipientBottomSheetDialogFragment.show(fragmentManager, state.from, null)
        }
      )

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = "Attachments",
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier
          .padding(8.dp)
          .fillMaxWidth()
      )

      if (state.attachments.isEmpty()) {
        Text(
          text = "None",
          modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth()
        )
      } else {
        state.attachments.forEachIndexed { i, attachment ->
          AttachmentBlock(
            attachment = attachment,
            onOffloadLocalData = onOffloadLocalData,
            onDeleteFromCdn = onDeleteFromCdn
          )

          if (i != state.attachments.lastIndex) {
            Dividers.Default()
          }
        }
      }
    }
  }
}

@Composable
private fun DetailRow(name: String, value: String, onClick: () -> Unit) {
  val formattedString = buildAnnotatedString {
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
      append("$name: ")
    }
    withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
      append(value)
    }
  }

  Text(
    text = formattedString,
    modifier = Modifier
      .clickable { onClick() }
      .padding(8.dp)
      .fillMaxWidth()
  )
}

@Composable
private fun ClickToCopyRow(name: String, value: String, valueToCopy: String = value) {
  val context: Context = LocalContext.current

  DetailRow(
    name = name,
    value = value,
    onClick = {
      Util.copyToClipboard(context, valueToCopy)
      Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }
  )
}

@Composable
private fun AttachmentBlock(
  attachment: AttachmentInfo,
  onOffloadLocalData: (Long) -> Unit,
  onDeleteFromCdn: (Long, Boolean) -> Unit
) {
  ClickToCopyRow(
    name = "ID",
    value = attachment.id.toString()
  )
  ClickToCopyRow(
    name = "Filename",
    value = attachment.fileName.toString()
  )
  ClickToCopyRow(
    name = "Content Type",
    value = attachment.contentType ?: "null"
  )
  ClickToCopyRow(
    name = "Quote Target Content Type",
    value = attachment.quoteTargetContentType ?: "Not a quote"
  )
  ClickToCopyRow(
    name = "Start Hash",
    value = attachment.hashStart ?: "null"
  )
  ClickToCopyRow(
    name = "End Hash",
    value = attachment.hashEnd ?: "null"
  )
  ClickToCopyRow(
    name = "Transform Properties",
    value = attachment.transformProperties ?: "null"
  )
  ClickToCopyRow(
    name = "Has Local Data",
    value = attachment.hasLocalData.toString()
  )
  ClickToCopyRow(
    name = "Transfer State",
    value = attachment.transferState.toString()
  )
  ClickToCopyRow(
    name = "Archive CDN",
    value = attachment.archiveCdn?.toString() ?: "null"
  )
  ClickToCopyRow(
    name = "Archive Transfer State",
    value = attachment.archiveTransferState.name
  )
  ClickToCopyRow(
    name = "Archive Thumbnail Transfer State",
    value = attachment.archiveThumbnailTransferState.name
  )

  DestructiveActions(
    attachment = attachment,
    onOffloadLocalData = onOffloadLocalData,
    onDeleteFromCdn = onDeleteFromCdn
  )
}

/**
 * Collapsed by default, and each action confirms, because these are irreversible and sit in a list people scroll through to copy values.
 */
@Composable
private fun DestructiveActions(
  attachment: AttachmentInfo,
  onOffloadLocalData: (Long) -> Unit,
  onDeleteFromCdn: (Long, Boolean) -> Unit
) {
  var expanded by remember(attachment.id) { mutableStateOf(false) }
  var pendingAction by remember(attachment.id) { mutableStateOf<PendingAction?>(null) }

  Text(
    text = if (expanded) "▾ Destructive test actions" else "▸ Destructive test actions",
    style = MaterialTheme.typography.labelLarge,
    modifier = Modifier
      .clickable { expanded = !expanded }
      .padding(8.dp)
      .fillMaxWidth()
  )

  if (!expanded) {
    return
  }

  val isArchived = attachment.archiveTransferState == AttachmentTable.ArchiveTransferState.FINISHED && attachment.archiveCdn != null

  Buttons.MediumTonal(
    onClick = { pendingAction = PendingAction.OFFLOAD },
    modifier = Modifier
      .padding(horizontal = 8.dp)
      .fillMaxWidth()
  ) {
    Text(text = "Offload local data")
  }

  Buttons.MediumTonal(
    onClick = { pendingAction = PendingAction.DELETE_FULL_SIZE },
    modifier = Modifier
      .padding(horizontal = 8.dp)
      .fillMaxWidth()
  ) {
    Text(text = "Delete full-size from CDN")
  }

  Buttons.MediumTonal(
    onClick = { pendingAction = PendingAction.DELETE_THUMBNAIL },
    modifier = Modifier
      .padding(horizontal = 8.dp)
      .fillMaxWidth()
  ) {
    Text(text = "Delete thumbnail from CDN")
  }

  when (pendingAction) {
    null -> Unit

    PendingAction.OFFLOAD -> Dialogs.SimpleAlertDialog(
      title = "Offload local data?",
      body = if (isArchived) {
        "Drops the local bytes for attachment ${attachment.id}. The archive CDN copy stays, so it can be restored by tapping the attachment."
      } else {
        "This attachment is NOT on the archive CDN (state ${attachment.archiveTransferState.name}, cdn ${attachment.archiveCdn ?: "null"}). Offloading it deletes the only copy and it cannot be restored."
      },
      confirm = if (isArchived) "Offload" else "Delete the only copy",
      dismiss = "Cancel",
      onConfirm = { onOffloadLocalData(attachment.id) },
      onDismiss = { pendingAction = null }
    )

    PendingAction.DELETE_FULL_SIZE -> Dialogs.SimpleAlertDialog(
      title = "Delete full-size from CDN?",
      body = "Immediately and irreversibly removes the full-size copy of attachment ${attachment.id} from the archive CDN. If the local bytes are ever offloaded, the media is gone for good.",
      confirm = "Delete from CDN",
      dismiss = "Cancel",
      onConfirm = { onDeleteFromCdn(attachment.id, false) },
      onDismiss = { pendingAction = null }
    )

    PendingAction.DELETE_THUMBNAIL -> Dialogs.SimpleAlertDialog(
      title = "Delete thumbnail from CDN?",
      body = "Immediately and irreversibly removes the thumbnail for attachment ${attachment.id} from the archive CDN. The full-size copy is untouched.",
      confirm = "Delete from CDN",
      dismiss = "Cancel",
      onConfirm = { onDeleteFromCdn(attachment.id, true) },
      onDismiss = { pendingAction = null }
    )
  }
}

private enum class PendingAction {
  OFFLOAD,
  DELETE_FULL_SIZE,
  DELETE_THUMBNAIL
}

@Preview
@Composable
private fun ContentPreview() {
  Content(
    ViewState(
      id = 1,
      sentTimestamp = 2,
      receivedTimestamp = 3,
      serverSentTimestamp = 4,
      to = RecipientId.from(1),
      from = RecipientId.from(2),
      attachments = emptyList()
    )
  )
}
