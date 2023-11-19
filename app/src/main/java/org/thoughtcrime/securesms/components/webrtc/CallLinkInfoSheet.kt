/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ShareCompat
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.toLiveData
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.kotlin.subscribeBy
import kotlinx.collections.immutable.toImmutableList
import org.signal.core.ui.BottomSheets
import org.signal.core.ui.Dividers
import org.signal.core.ui.Rows
import org.signal.core.ui.theme.SignalTheme
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.core.util.concurrent.addTo
import org.signal.core.util.logging.Log
import org.signal.ringrtc.CallLinkState.Restrictions
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.calls.links.CallLinks
import org.thoughtcrime.securesms.calls.links.EditCallLinkNameDialogFragment
import org.thoughtcrime.securesms.calls.links.EditCallLinkNameDialogFragmentArgs
import org.thoughtcrime.securesms.calls.links.SignalCallRow
import org.thoughtcrime.securesms.calls.links.details.CallLinkDetailsViewModel
import org.thoughtcrime.securesms.components.AvatarImageView
import org.thoughtcrime.securesms.compose.ComposeBottomSheetDialogFragment
import org.thoughtcrime.securesms.database.CallLinkTable
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.events.CallParticipant
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.service.webrtc.links.CallLinkCredentials
import org.thoughtcrime.securesms.service.webrtc.links.CallLinkRoomId
import org.thoughtcrime.securesms.service.webrtc.links.SignalCallLinkState
import org.thoughtcrime.securesms.service.webrtc.links.UpdateCallLinkResult
import org.thoughtcrime.securesms.util.BottomSheetUtil

/**
 * Displays information about the in-progress CallLink call from
 * within WebRtcActivity. If the user is able to modify call link
 * state, provides options to do so.
 */
class CallLinkInfoSheet : ComposeBottomSheetDialogFragment() {

  companion object {

    private val TAG = Log.tag(CallLinkInfoSheet::class.java)
    private const val CALL_LINK_ROOM_ID = "call_link_room_id"

    @JvmStatic
    fun show(fragmentManager: FragmentManager, callLinkRoomId: CallLinkRoomId) {
      CallLinkInfoSheet().apply {
        arguments = bundleOf(CALL_LINK_ROOM_ID to callLinkRoomId)
      }.show(fragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
    }
  }

  override val forceDarkTheme = true

  private val webRtcCallViewModel: WebRtcCallViewModel by activityViewModels()
  private val callLinkDetailsViewModel: CallLinkDetailsViewModel by viewModels(factoryProducer = {
    CallLinkDetailsViewModel.Factory(BundleCompat.getParcelable(requireArguments(), CALL_LINK_ROOM_ID, CallLinkRoomId::class.java)!!)
  })

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
  override fun SheetContent() {
    val callLinkDetailsState by callLinkDetailsViewModel.state
    val callParticipantsState by webRtcCallViewModel.callParticipantsState
      .toFlowable(BackpressureStrategy.LATEST)
      .toLiveData()
      .observeAsState()

    val participants: List<CallParticipant> by webRtcCallViewModel.callParticipantsState
      .toFlowable(BackpressureStrategy.LATEST)
      .map { state -> state.allRemoteParticipants }
      .toLiveData()
      .observeAsState(emptyList())

    val onEditNameClicked: () -> Unit = remember(callLinkDetailsState) {
      {
        EditCallLinkNameDialogFragment().apply {
          arguments = EditCallLinkNameDialogFragmentArgs.Builder(callLinkDetailsState.callLink?.state?.name ?: "").build().toBundle()
        }.show(parentFragmentManager, null)
      }
    }

    val callLink = callLinkDetailsState.callLink
    if (callLink != null) {
      Sheet(
        callLink = callLink,
        participants = participants,
        onShareLinkClicked = this::shareLink,
        onEditNameClicked = onEditNameClicked,
        onToggleAdminApprovalClicked = this::onApproveAllMembersChanged,
        onBlock = this::onBlockParticipant
      )
    }
  }

  private fun onBlockParticipant(callParticipant: CallParticipant) {
    MaterialAlertDialogBuilder(requireContext())
      .setNegativeButton(android.R.string.cancel, null)
      .setMessage(getString(R.string.CallLinkInfoSheet__remove_s_from_the_call, callParticipant.recipient.getShortDisplayName(requireContext())))
      .setPositiveButton(R.string.CallLinkInfoSheet__remove) { _, _ ->
        ApplicationDependencies.getSignalCallManager().removeFromCallLink(callParticipant)
      }
      .setNeutralButton(R.string.CallLinkInfoSheet__block_from_call) { _, _ ->
        ApplicationDependencies.getSignalCallManager().blockFromCallLink(callParticipant)
      }
      .show()
  }

  private fun onApproveAllMembersChanged(checked: Boolean) {
    callLinkDetailsViewModel.setApproveAllMembers(checked)
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeBy(onSuccess = {
        if (it !is UpdateCallLinkResult.Success) {
          Log.w(TAG, "Failed to change restrictions. $it")
          toastFailure()
        }
      }, onError = handleError("onApproveAllMembersChanged"))
      .addTo(lifecycleDisposable)
  }

  private fun shareLink() {
    val mimeType = Intent.normalizeMimeType("text/plain")
    val shareIntent = ShareCompat.IntentBuilder(requireContext())
      .setText(CallLinks.url(callLinkDetailsViewModel.rootKeySnapshot))
      .setType(mimeType)
      .createChooserIntent()

    try {
      startActivity(shareIntent)
    } catch (e: ActivityNotFoundException) {
      Toast.makeText(requireContext(), R.string.CreateCallLinkBottomSheetDialogFragment__failed_to_open_share_sheet, Toast.LENGTH_LONG).show()
    }
  }

  private fun setName(name: String) {
    callLinkDetailsViewModel.setName(name)
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeBy(
        onSuccess = {
          if (it !is UpdateCallLinkResult.Success) {
            Log.w(TAG, "Failed to set name. $it")
            toastFailure()
          }
        },
        onError = handleError("setName")
      )
      .addTo(lifecycleDisposable)
  }

  private fun handleError(method: String): (throwable: Throwable) -> Unit {
    return {
      Log.w(TAG, "Failure during $method", it)
      toastFailure()
    }
  }

  private fun toastFailure() {
    Toast.makeText(requireContext(), R.string.CallLinkDetailsFragment__couldnt_save_changes, Toast.LENGTH_LONG).show()
  }
}

@Preview
@Composable
private fun SheetPreview() {
  SignalTheme(isDarkMode = true) {
    Surface {
      Sheet(
        callLink = CallLinkTable.CallLink(
          recipientId = RecipientId.UNKNOWN,
          roomId = CallLinkRoomId.fromBytes(byteArrayOf(1, 2, 3, 4, 5)),
          credentials = CallLinkCredentials(
            linkKeyBytes = byteArrayOf(1, 2, 3, 4, 5),
            adminPassBytes = byteArrayOf(1, 2, 3, 4, 5)
          ),
          state = SignalCallLinkState()
        ),
        participants = listOf(CallParticipant(recipient = Recipient.UNKNOWN)).toImmutableList(),
        onShareLinkClicked = {},
        onEditNameClicked = {},
        onToggleAdminApprovalClicked = {},
        onBlock = {}
      )
    }
  }
}

@Composable
private fun Sheet(
  callLink: CallLinkTable.CallLink,
  participants: List<CallParticipant>,
  onShareLinkClicked: () -> Unit,
  onEditNameClicked: () -> Unit,
  onToggleAdminApprovalClicked: (Boolean) -> Unit,
  onBlock: (CallParticipant) -> Unit
) {
  LazyColumn(
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    item {
      BottomSheets.Handle()

      Text(
        text = stringResource(id = R.string.CallLinkInfoSheet__call_info),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(vertical = 24.dp)
      )

      SignalCallRow(callLink = callLink, onJoinClicked = null)
      Rows.TextRow(
        text = stringResource(id = R.string.CallLinkDetailsFragment__share_link),
        icon = ImageVector.vectorResource(id = R.drawable.symbol_link_24),
        iconModifier = Modifier
          .background(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = CircleShape
          )
          .size(42.dp)
          .padding(9.dp),
        modifier = Modifier
          .defaultMinSize(minHeight = 64.dp)
          .clickable(onClick = onShareLinkClicked)
      )
    }

    items(participants, { it.callParticipantId }, { null }) {
      CallLinkMemberRow(
        callParticipant = it,
        isSelfAdmin = callLink.credentials?.adminPassBytes != null,
        onBlockClicked = onBlock
      )
    }

    if (callLink.credentials?.adminPassBytes != null) {
      item {
        Dividers.Default()
        Rows.TextRow(
          text = stringResource(id = R.string.CallLinkDetailsFragment__add_call_name),
          modifier = Modifier.clickable(onClick = onEditNameClicked)
        )
        Rows.ToggleRow(
          checked = callLink.state.restrictions == Restrictions.ADMIN_APPROVAL,
          text = stringResource(id = R.string.CallLinkDetailsFragment__approve_all_members),
          onCheckChanged = onToggleAdminApprovalClicked
        )
      }
    }
  }
}

@Preview
@Composable
private fun CallLinkMemberRowPreview() {
  SignalTheme(isDarkMode = true) {
    Surface {
      CallLinkMemberRow(
        CallParticipant(recipient = Recipient.UNKNOWN),
        isSelfAdmin = true,
        {}
      )
    }
  }
}

@Composable
private fun CallLinkMemberRow(
  callParticipant: CallParticipant,
  isSelfAdmin: Boolean,
  onBlockClicked: (CallParticipant) -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(Rows.defaultPadding())
  ) {
    val recipient by Recipient.observable(callParticipant.recipient.id)
      .toFlowable(BackpressureStrategy.LATEST)
      .toLiveData()
      .observeAsState(initial = callParticipant.recipient)

    if (LocalInspectionMode.current) {
      Spacer(
        modifier = Modifier
          .size(40.dp)
          .background(color = Color.Red, shape = CircleShape)
      )
    } else {
      AndroidView(
        factory = ::AvatarImageView,
        modifier = Modifier.size(40.dp)
      ) {
        it.setAvatarUsingProfile(recipient)
      }
    }

    Spacer(modifier = Modifier.width(24.dp))

    Text(
      text = recipient.getShortDisplayName(LocalContext.current),
      modifier = Modifier
        .weight(1f)
        .align(Alignment.CenterVertically)
    )

    if (isSelfAdmin && !recipient.isSelf) {
      Icon(
        imageVector = ImageVector.vectorResource(id = R.drawable.symbol_minus_circle_24),
        contentDescription = null,
        modifier = Modifier
          .clickable(onClick = { onBlockClicked(callParticipant) })
          .align(Alignment.CenterVertically)
      )
    }
  }
}
