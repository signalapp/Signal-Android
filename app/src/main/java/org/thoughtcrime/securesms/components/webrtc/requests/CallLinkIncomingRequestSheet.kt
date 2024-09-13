/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.requests

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rxjava3.subscribeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import org.signal.core.ui.BottomSheets
import org.signal.core.ui.DarkPreview
import org.signal.core.ui.Dividers
import org.signal.core.ui.Previews
import org.signal.core.ui.Rows
import org.signal.core.util.getParcelableCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.AvatarImage
import org.thoughtcrime.securesms.components.AvatarImageView
import org.thoughtcrime.securesms.compose.ComposeBottomSheetDialogFragment
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.BottomSheetUtil
import org.thoughtcrime.securesms.util.viewModel

/**
 * Displayed when the user presses the user avatar in the call link join request
 * bar.
 */
class CallLinkIncomingRequestSheet : ComposeBottomSheetDialogFragment() {

  companion object {
    private const val RECIPIENT_ID = "recipient_id"

    @JvmStatic
    fun show(fragmentManager: FragmentManager, recipientId: RecipientId) {
      CallLinkIncomingRequestSheet().apply {
        arguments = bundleOf(
          RECIPIENT_ID to recipientId
        )
      }.show(fragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
    }
  }

  override val forceDarkTheme = true

  private val recipientId: RecipientId by lazy {
    requireArguments().getParcelableCompat(RECIPIENT_ID, RecipientId::class.java)!!
  }

  private val viewModel by viewModel {
    CallLinkIncomingRequestViewModel(recipientId)
  }

  @Composable
  override fun SheetContent() {
    val state = viewModel.observeState(LocalContext.current).subscribeAsState(initial = CallLinkIncomingRequestState())
    if (state.value.recipient == Recipient.UNKNOWN) {
      return
    }

    CallLinkIncomingRequestSheetContent(
      state = state.value,
      onApproveEntry = this::onApproveEntry,
      onDenyEntry = this::onDenyEntry
    )
  }

  private fun onApproveEntry() {
    AppDependencies.signalCallManager.setCallLinkJoinRequestAccepted(recipientId)
    dismissAllowingStateLoss()
  }

  private fun onDenyEntry() {
    AppDependencies.signalCallManager.setCallLinkJoinRequestRejected(recipientId)
    dismissAllowingStateLoss()
  }
}

@DarkPreview
@Composable
private fun CallLinkIncomingRequestSheetContentPreview() {
  Previews.BottomSheetPreview {
    CallLinkIncomingRequestSheetContent(
      state = CallLinkIncomingRequestState(
        name = "Miles Morales",
        subtitle = "+1 (555) 555-5555",
        groupsInCommon = "Member of Webheads, Group B, Group C, Group D, and 83 others.",
        isSystemContact = true
      ),
      onApproveEntry = {},
      onDenyEntry = {}
    )
  }
}

@Composable
private fun CallLinkIncomingRequestSheetContent(
  state: CallLinkIncomingRequestState,
  onApproveEntry: () -> Unit,
  onDenyEntry: () -> Unit
) {
  LazyColumn(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    item { BottomSheets.Handle() }
    item { AvatarImage(recipient = state.recipient, modifier = Modifier.size(80.dp)) }
    item {
      Title(
        recipientName = state.name,
        isSystemContact = state.isSystemContact
      )
    }

    if (state.subtitle.isNotEmpty()) {
      item {
        Text(
          text = state.subtitle,
          modifier = Modifier.padding(4.dp)
        )
      }
    }

    if (state.groupsInCommon.isNotEmpty()) {
      item {
        Text(
          text = state.groupsInCommon,
          textAlign = TextAlign.Center,
          style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier.padding(vertical = 6.dp, horizontal = dimensionResource(R.dimen.core_ui__gutter))
        )
      }
    }

    item {
      Dividers.Default()
    }

    item {
      Rows.TextRow(
        text = stringResource(id = R.string.CallLinkIncomingRequestSheet__approve_entry),
        icon = painterResource(R.drawable.symbol_check_circle_24),
        onClick = onApproveEntry
      )
    }

    item {
      Rows.TextRow(
        text = stringResource(id = R.string.CallLinkIncomingRequestSheet__deny_entry),
        icon = painterResource(R.drawable.symbol_x_circle_24),
        onClick = onDenyEntry
      )
    }

    item {
      Spacer(modifier = Modifier.size(32.dp))
    }
  }
}

@Composable
private fun Avatar(
  recipient: Recipient
) {
  if (LocalInspectionMode.current) {
    Spacer(
      modifier = Modifier
        .padding(top = 13.dp)
        .size(80.dp)
        .background(color = Color.Red, shape = CircleShape)
    )
  } else {
    AndroidView(
      factory = ::AvatarImageView,
      modifier = Modifier
        .size(80.dp)
        .padding(top = 13.dp)
    ) {
      it.setAvatarUsingProfile(recipient)
    }
  }
}

@Composable
private fun Title(
  recipientName: String,
  isSystemContact: Boolean
) {
  if (isSystemContact) {
    Row(modifier = Modifier.padding(top = 12.dp)) {
      Text(
        text = recipientName,
        style = MaterialTheme.typography.headlineMedium
      )
      Icon(
        painter = painterResource(id = R.drawable.symbol_person_circle_24),
        contentDescription = null,
        modifier = Modifier
          .padding(start = 6.dp)
          .align(CenterVertically)
      )
    }
  } else {
    Text(
      text = recipientName,
      style = MaterialTheme.typography.headlineMedium
    )
  }
}
