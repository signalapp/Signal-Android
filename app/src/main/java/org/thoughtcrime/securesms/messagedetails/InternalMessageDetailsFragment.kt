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
import androidx.compose.runtime.getValue
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
import org.thoughtcrime.securesms.compose.ComposeFullScreenDialogFragment
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.messagedetails.InternalMessageDetailsViewModel.AttachmentInfo
import org.thoughtcrime.securesms.messagedetails.InternalMessageDetailsViewModel.ViewState
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.ui.bottomsheet.RecipientBottomSheetDialogFragment
import org.thoughtcrime.securesms.util.Util
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

    state?.let {
      Content(it)
    }
  }
}

@Composable
private fun Content(state: ViewState) {
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
        state.attachments.forEach { attachment ->
          AttachmentBlock(attachment)
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
private fun AttachmentBlock(attachment: AttachmentInfo) {
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
