/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rxjava3.subscribeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import org.signal.core.ui.BottomSheets
import org.signal.core.ui.Buttons
import org.signal.core.ui.theme.SignalTheme
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.AvatarImageView
import org.thoughtcrime.securesms.compose.ComposeBottomSheetDialogFragment
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.service.webrtc.PendingParticipantCollection
import org.thoughtcrime.securesms.util.activityViewModel
import kotlin.time.Duration.Companion.milliseconds

/**
 * Displays a list of pending participants attempting to join this call.
 */
class PendingParticipantsBottomSheet : ComposeBottomSheetDialogFragment() {

  companion object {
    const val REQUEST_KEY = "PendingParticipantsBottomSheet_result"
  }

  private val viewModel: WebRtcCallViewModel by activityViewModel {
    error("Should already exist")
  }

  override val peekHeightPercentage: Float = 1f

  @Composable
  override fun SheetContent() {
    val launchTime = remember {
      System.currentTimeMillis().milliseconds
    }

    val participants = viewModel.pendingParticipants
      .map { it.getAllPendingParticipants(launchTime).toList() }
      .subscribeAsState(initial = emptyList())

    PendingParticipantsSheet(
      pendingParticipants = participants.value,
      onApproveAll = this::onApproveAll,
      onDenyAll = this::onDenyAll,
      onApprove = this::onApprove,
      onDeny = this::onDeny
    )
  }

  private fun onApprove(recipient: Recipient) {
    ApplicationDependencies.getSignalCallManager().setCallLinkJoinRequestAccepted(recipient)
  }

  private fun onDeny(recipient: Recipient) {
    ApplicationDependencies.getSignalCallManager().setCallLinkJoinRequestRejected(recipient)
  }

  private fun onApproveAll() {
    dismiss()
    setFragmentResult(REQUEST_KEY, bundleOf(REQUEST_KEY to true))
  }

  private fun onDenyAll() {
    dismiss()
    setFragmentResult(REQUEST_KEY, bundleOf(REQUEST_KEY to false))
  }
}

@Preview(showSystemUi = true)
@Composable
private fun PendingParticipantsSheetPreview() {
  SignalTheme(isDarkMode = true) {
    Surface(shape = RoundedCornerShape(18.dp, 18.dp)) {
      PendingParticipantsSheet(
        pendingParticipants = (1 until 7).map {
          PendingParticipantCollection.Entry(Recipient.UNKNOWN, PendingParticipantCollection.State.PENDING, System.currentTimeMillis().milliseconds)
        },
        onApproveAll = {},
        onDenyAll = {},
        onApprove = {},
        onDeny = {}
      )
    }
  }
}

@Composable
private fun PendingParticipantsSheet(
  pendingParticipants: List<PendingParticipantCollection.Entry>,
  onApproveAll: () -> Unit,
  onDenyAll: () -> Unit,
  onApprove: (Recipient) -> Unit,
  onDeny: (Recipient) -> Unit
) {
  Box {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier.padding(bottom = 64.dp)
    ) {
      BottomSheets.Handle()

      Spacer(Modifier.size(14.dp))

      LazyColumn(
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        item {
          Text(
            text = stringResource(id = R.string.PendingParticipantsBottomSheet__requests_to_join_this_call),
            style = MaterialTheme.typography.titleLarge
          )
        }

        item {
          Text(
            text = pluralStringResource(
              id = R.plurals.PendingParticipantsBottomSheet__d_people_waiting,
              count = pendingParticipants.size,
              pendingParticipants.size
            ),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        item {
          Spacer(Modifier.size(24.dp))
        }

        items(pendingParticipants.size) { index ->
          PendingParticipantRow(
            participant = pendingParticipants[index],
            onApprove = onApprove,
            onDeny = onDeny
          )
        }
      }
    }

    Row(
      modifier = Modifier
        .align(Alignment.BottomStart)
        .background(color = MaterialTheme.colorScheme.background)
        .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
      Spacer(modifier = Modifier.weight(1f))

      TextButton(
        onClick = onDenyAll
      ) {
        Text(
          text = "Deny all",
          color = MaterialTheme.colorScheme.onSurface
        )
      }

      Spacer(modifier = Modifier.size(8.dp))

      Buttons.LargeTonal(onClick = onApproveAll) {
        Text(
          text = "Approve all"
        )
      }
    }
  }
}

@Composable
private fun PendingParticipantRow(
  participant: PendingParticipantCollection.Entry,
  onApprove: (Recipient) -> Unit,
  onDeny: (Recipient) -> Unit
) {
  val onApproveCallback = remember(participant.recipient) { { onApprove(participant.recipient) } }
  val onDenyCallback = remember(participant.recipient) { { onDeny(participant.recipient) } }

  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
  ) {
    PendingParticipantAvatar(recipient = participant.recipient)

    Text(
      text = participant.recipient.getDisplayName(LocalContext.current),
      style = MaterialTheme.typography.bodyLarge,
      modifier = Modifier
        .weight(1f)
        .padding(horizontal = 16.dp)
    )

    CircularIconButton(
      symbol = ImageVector.vectorResource(id = R.drawable.symbol_x_compact_bold_16),
      contentDescription = stringResource(id = R.string.PendingParticipantsBottomSheet__reject),
      backgroundColor = colorResource(id = R.color.webrtc_hangup_background),
      onClick = onDenyCallback
    )

    Spacer(modifier = Modifier.size(24.dp))

    CircularIconButton(
      symbol = ImageVector.vectorResource(id = R.drawable.symbol_check_compact_bold_16),
      contentDescription = stringResource(id = R.string.PendingParticipantsBottomSheet__approve),
      backgroundColor = colorResource(id = R.color.signal_accent_green),
      onClick = onApproveCallback
    )
  }
}

@Composable
private fun CircularIconButton(
  symbol: ImageVector,
  contentDescription: String?,
  backgroundColor: Color,
  onClick: () -> Unit
) {
  Icon(
    imageVector = symbol,
    contentDescription = contentDescription,
    modifier = Modifier
      .size(28.dp)
      .background(
        color = backgroundColor,
        shape = CircleShape
      )
      .clickable(onClick = onClick)
      .padding(6.dp)
  )
}

@Composable
private fun PendingParticipantAvatar(recipient: Recipient) {
  if (LocalInspectionMode.current) {
    Icon(
      imageVector = Icons.Default.Person,
      contentDescription = null,
      modifier = Modifier
        .size(40.dp)
        .background(
          color = Color.Red,
          shape = CircleShape
        )
    )
  } else {
    AndroidView(
      factory = ::AvatarImageView,
      modifier = Modifier.size(40.dp)
    ) {
      it.setAvatar(recipient)
    }
  }
}
