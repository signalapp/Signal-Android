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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.ShareCompat
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.signal.core.ui.Dialogs
import org.signal.core.ui.Dividers
import org.signal.core.ui.Rows
import org.signal.core.ui.Scaffolds
import org.signal.core.ui.theme.SignalTheme
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.core.util.logging.Log
import org.signal.ringrtc.CallLinkState.Restrictions
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.calls.YouAreAlreadyInACallSnackbar
import org.thoughtcrime.securesms.calls.YouAreAlreadyInACallSnackbar.YouAreAlreadyInACallSnackbar
import org.thoughtcrime.securesms.calls.links.CallLinks
import org.thoughtcrime.securesms.calls.links.EditCallLinkNameDialogFragment
import org.thoughtcrime.securesms.calls.links.SignalCallRow
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.database.CallLinkTable
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.service.webrtc.links.CallLinkCredentials
import org.thoughtcrime.securesms.service.webrtc.links.CallLinkRoomId
import org.thoughtcrime.securesms.service.webrtc.links.SignalCallLinkState
import org.thoughtcrime.securesms.service.webrtc.links.UpdateCallLinkResult
import org.thoughtcrime.securesms.sharing.v2.ShareActivity
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.Util
import java.time.Instant

/**
 * Provides detailed info about a call link and allows the owner of that link
 * to modify call properties.
 */
class CallLinkDetailsFragment : ComposeFragment(), CallLinkDetailsCallback {

  companion object {
    private val TAG = Log.tag(CallLinkDetailsFragment::class.java)
  }

  private val args: CallLinkDetailsFragmentArgs by navArgs()
  private val viewModel: CallLinkDetailsViewModel by viewModels(factoryProducer = {
    CallLinkDetailsViewModel.Factory(args.roomId)
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
  override fun FragmentContent() {
    val state by viewModel.state
    val showAlreadyInACall by viewModel.showAlreadyInACall.collectAsState(false)

    CallLinkDetails(
      state,
      showAlreadyInACall,
      this
    )
  }

  override fun onNavigationClicked() {
    ActivityCompat.finishAfterTransition(requireActivity())
  }

  override fun onJoinClicked() {
    val recipientSnapshot = viewModel.recipientSnapshot
    if (recipientSnapshot != null) {
      CommunicationActions.startVideoCall(this, recipientSnapshot) {
        viewModel.showAlreadyInACall(true)
      }
    }
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

  override fun onCopyClicked() {
    Util.copyToClipboard(requireContext(), CallLinks.url(viewModel.rootKeySnapshot))
    Toast.makeText(requireContext(), R.string.CreateCallLinkBottomSheetDialogFragment__copied_to_clipboard, Toast.LENGTH_LONG).show()
  }

  override fun onShareLinkViaSignalClicked() {
    startActivity(
      ShareActivity.sendSimpleText(
        requireContext(),
        getString(R.string.CreateCallLink__use_this_link_to_join_a_signal_call, CallLinks.url(viewModel.rootKeySnapshot))
      )
    )
  }

  override fun onDeleteClicked() {
    viewModel.setDisplayRevocationDialog(true)
  }

  override fun onDeleteConfirmed() {
    viewModel.setDisplayRevocationDialog(false)
    lifecycleDisposable += viewModel.delete().observeOn(AndroidSchedulers.mainThread()).subscribeBy(onSuccess = {
      when (it) {
        is UpdateCallLinkResult.Delete -> ActivityCompat.finishAfterTransition(requireActivity())
        else -> {
          Log.w(TAG, "Failed to revoke. $it")
          toastFailure()
        }
      }
    }, onError = handleError("onDeleteClicked"))
  }

  override fun onDeleteCanceled() {
    viewModel.setDisplayRevocationDialog(false)
  }

  override fun onApproveAllMembersChanged(checked: Boolean) {
    lifecycleDisposable += viewModel.setApproveAllMembers(checked).observeOn(AndroidSchedulers.mainThread()).subscribeBy(onSuccess = {
      if (it !is UpdateCallLinkResult.Update) {
        Log.w(TAG, "Failed to change restrictions. $it")
        toastFailure()
      }
    }, onError = handleError("onApproveAllMembersChanged"))
  }

  private fun setName(name: String) {
    lifecycleDisposable += viewModel.setName(name).observeOn(AndroidSchedulers.mainThread()).subscribeBy(onSuccess = {
      if (it !is UpdateCallLinkResult.Update) {
        Log.w(TAG, "Failed to set name. $it")
        toastFailure()
      }
    }, onError = handleError("setName"))
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

private interface CallLinkDetailsCallback {
  fun onNavigationClicked()
  fun onJoinClicked()
  fun onEditNameClicked()
  fun onShareClicked()
  fun onCopyClicked()
  fun onShareLinkViaSignalClicked()
  fun onDeleteClicked()
  fun onDeleteConfirmed()
  fun onDeleteCanceled()
  fun onApproveAllMembersChanged(checked: Boolean)
}

@Preview
@Composable
private fun CallLinkDetailsPreview() {
  val callLink = remember {
    val credentials = CallLinkCredentials(
      byteArrayOf(1, 2, 3, 4),
      byteArrayOf(3, 4, 5, 6)
    )
    CallLinkTable.CallLink(
      recipientId = RecipientId.UNKNOWN,
      roomId = CallLinkRoomId.fromBytes(byteArrayOf(1, 2, 3, 4)),
      credentials = credentials,
      state = SignalCallLinkState(
        name = "Call Name",
        revoked = false,
        restrictions = Restrictions.NONE,
        expiration = Instant.MAX
      ),
      deletionTimestamp = 0L
    )
  }

  SignalTheme(false) {
    CallLinkDetails(
      CallLinkDetailsState(
        false,
        callLink
      ),
      true,
      object : CallLinkDetailsCallback {
        override fun onDeleteConfirmed() = Unit
        override fun onDeleteCanceled() = Unit
        override fun onNavigationClicked() = Unit
        override fun onJoinClicked() = Unit
        override fun onEditNameClicked() = Unit
        override fun onShareClicked() = Unit
        override fun onCopyClicked() = Unit
        override fun onShareLinkViaSignalClicked() = Unit
        override fun onDeleteClicked() = Unit
        override fun onApproveAllMembersChanged(checked: Boolean) = Unit
      }
    )
  }
}

@Composable
private fun CallLinkDetails(
  state: CallLinkDetailsState,
  showAlreadyInACall: Boolean,
  callback: CallLinkDetailsCallback
) {
  Scaffolds.Settings(
    title = stringResource(id = R.string.CallLinkDetailsFragment__call_details),
    snackbarHost = {
      YouAreAlreadyInACallSnackbar(showAlreadyInACall)
    },
    onNavigationClick = callback::onNavigationClicked,
    navigationIconPainter = painterResource(id = R.drawable.ic_arrow_left_24)
  ) { paddingValues ->
    if (state.callLink == null) {
      return@Settings
    }

    Column(modifier = Modifier.padding(paddingValues)) {
      SignalCallRow(
        callLink = state.callLink,
        callLinkPeekInfo = state.peekInfo,
        onJoinClicked = callback::onJoinClicked,
        modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
      )

      if (state.callLink.credentials?.adminPassBytes != null) {
        Rows.TextRow(
          text = stringResource(
            id = if (state.callLink.state.name.isEmpty()) {
              R.string.CreateCallLinkBottomSheetDialogFragment__add_call_name
            } else {
              R.string.CreateCallLinkBottomSheetDialogFragment__edit_call_name
            }
          ),
          onClick = callback::onEditNameClicked
        )

        Rows.ToggleRow(
          checked = state.callLink.state.restrictions == Restrictions.ADMIN_APPROVAL,
          text = stringResource(id = R.string.CallLinkDetailsFragment__require_admin_approval),
          onCheckChanged = callback::onApproveAllMembersChanged
        )

        Dividers.Default()
      }

      Rows.TextRow(
        text = stringResource(id = R.string.CreateCallLinkBottomSheetDialogFragment__share_link_via_signal),
        icon = painterResource(id = R.drawable.symbol_forward_24),
        onClick = callback::onShareLinkViaSignalClicked
      )

      Rows.TextRow(
        text = stringResource(id = R.string.CreateCallLinkBottomSheetDialogFragment__copy_link),
        icon = painterResource(id = R.drawable.symbol_copy_android_24),
        onClick = callback::onCopyClicked
      )

      Rows.TextRow(
        text = stringResource(id = R.string.CallLinkDetailsFragment__share_link),
        icon = painterResource(id = R.drawable.symbol_link_24),
        onClick = callback::onShareClicked
      )

      Rows.TextRow(
        text = stringResource(id = R.string.CallLinkDetailsFragment__delete_call_link),
        icon = painterResource(id = R.drawable.symbol_trash_24),
        foregroundTint = MaterialTheme.colorScheme.error,
        onClick = callback::onDeleteClicked
      )
    }

    if (state.displayRevocationDialog) {
      Dialogs.SimpleAlertDialog(
        title = stringResource(R.string.CallLinkDetailsFragment__delete_link),
        body = stringResource(id = R.string.CallLinkDetailsFragment__this_link_will_no_longer_work),
        confirm = stringResource(id = R.string.delete),
        dismiss = stringResource(id = android.R.string.cancel),
        onConfirm = callback::onDeleteConfirmed,
        onDismiss = callback::onDeleteCanceled
      )
    }
  }
}
