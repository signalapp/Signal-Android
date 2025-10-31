/**
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.calls.links.details

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ShareCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.Snackbars
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.ringrtc.CallLinkState.Restrictions
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.calls.YouAreAlreadyInACallSnackbar.YouAreAlreadyInACallSnackbar
import org.thoughtcrime.securesms.calls.links.CallLinks
import org.thoughtcrime.securesms.calls.links.SignalCallRow
import org.thoughtcrime.securesms.database.CallLinkTable
import org.thoughtcrime.securesms.main.MainNavigationDetailLocation
import org.thoughtcrime.securesms.main.MainNavigationListLocation
import org.thoughtcrime.securesms.main.MainNavigationRouter
import org.thoughtcrime.securesms.main.MainNavigationViewModel
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.service.webrtc.links.CallLinkCredentials
import org.thoughtcrime.securesms.service.webrtc.links.CallLinkRoomId
import org.thoughtcrime.securesms.service.webrtc.links.SignalCallLinkState
import org.thoughtcrime.securesms.sharing.v2.ShareActivity
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.Util
import org.thoughtcrime.securesms.window.isSplitPane
import java.time.Instant

@Composable
fun CallLinkDetailsScreen(
  roomId: CallLinkRoomId,
  viewModel: CallLinkDetailsViewModel = viewModel {
    CallLinkDetailsViewModel(roomId)
  },
  router: MainNavigationRouter = viewModel<MainNavigationViewModel>(viewModelStoreOwner = LocalContext.current as ComponentActivity) {
    error("Should already be created.")
  }
) {
  val activity = LocalContext.current as FragmentActivity
  val callback = remember {
    DefaultCallLinkDetailsCallback(
      activity = activity,
      viewModel = viewModel,
      router = router
    )
  }

  val state by viewModel.state.collectAsStateWithLifecycle(activity)
  val showAlreadyInACall by viewModel.showAlreadyInACall.collectAsStateWithLifecycle(initialValue = false, lifecycleOwner = activity)

  CallLinkDetailsScreen(
    state = state,
    showAlreadyInACall = showAlreadyInACall,
    callback = callback,
    showNavigationIcon = !currentWindowAdaptiveInfo().windowSizeClass.isSplitPane()
  )
}

class DefaultCallLinkDetailsCallback(
  private val activity: FragmentActivity,
  private val viewModel: CallLinkDetailsViewModel,
  private val router: MainNavigationRouter
) : CallLinkDetailsCallback {

  private val lifecycleDisposable = LifecycleDisposable()

  init {
    lifecycleDisposable.bindTo(activity)
  }

  override fun onNavigationClicked() {
    activity.onBackPressedDispatcher.onBackPressed()
  }

  override fun onJoinClicked() {
    val recipientSnapshot = viewModel.recipientSnapshot
    if (recipientSnapshot != null) {
      CommunicationActions.startVideoCall(activity, recipientSnapshot) {
        viewModel.showAlreadyInACall(true)
      }
    }
  }

  override fun onEditNameClicked() {
    router.goTo(MainNavigationDetailLocation.Calls.CallLinks.EditCallLinkName(callLinkRoomId = viewModel.recipientSnapshot!!.requireCallLinkRoomId()))
  }

  override fun onShareClicked() {
    val mimeType = Intent.normalizeMimeType("text/plain")
    val shareIntent = ShareCompat.IntentBuilder(activity)
      .setText(CallLinks.url(viewModel.rootKeySnapshot, viewModel.epochSnapshot))
      .setType(mimeType)
      .createChooserIntent()

    try {
      activity.startActivity(shareIntent)
    } catch (e: ActivityNotFoundException) {
      Toast.makeText(activity, R.string.CreateCallLinkBottomSheetDialogFragment__failed_to_open_share_sheet, Toast.LENGTH_LONG).show()
    }
  }

  override fun onCopyClicked() {
    Util.copyToClipboard(activity, CallLinks.url(viewModel.rootKeySnapshot, viewModel.epochSnapshot))
    Toast.makeText(activity, R.string.CreateCallLinkBottomSheetDialogFragment__copied_to_clipboard, Toast.LENGTH_LONG).show()
  }

  override fun onShareLinkViaSignalClicked() {
    activity.startActivity(
      ShareActivity.sendSimpleText(
        activity,
        activity.getString(R.string.CreateCallLink__use_this_link_to_join_a_signal_call, CallLinks.url(viewModel.rootKeySnapshot, viewModel.epochSnapshot))
      )
    )
  }

  override fun onDeleteClicked() {
    viewModel.setDisplayRevocationDialog(true)
  }

  override fun onDeleteConfirmed() {
    viewModel.setDisplayRevocationDialog(false)
    activity.lifecycleScope.launch {
      if (viewModel.delete()) {
        router.goTo(MainNavigationListLocation.CALLS)
        router.goTo(MainNavigationDetailLocation.Empty)
      }
    }
  }

  override fun onDeleteCanceled() {
    viewModel.setDisplayRevocationDialog(false)
  }

  override fun onApproveAllMembersChanged(checked: Boolean) {
    activity.lifecycleScope.launch {
      viewModel.setApproveAllMembers(checked)
    }
  }
}

interface CallLinkDetailsCallback {
  fun onNavigationClicked() = Unit
  fun onJoinClicked() = Unit
  fun onEditNameClicked() = Unit
  fun onShareClicked() = Unit
  fun onCopyClicked() = Unit
  fun onShareLinkViaSignalClicked() = Unit
  fun onDeleteClicked() = Unit
  fun onDeleteConfirmed() = Unit
  fun onDeleteCanceled() = Unit
  fun onApproveAllMembersChanged(checked: Boolean) = Unit

  object Empty : CallLinkDetailsCallback
}

@Composable
fun CallLinkDetailsScreen(
  state: CallLinkDetailsState,
  showAlreadyInACall: Boolean,
  callback: CallLinkDetailsCallback,
  showNavigationIcon: Boolean = true
) {
  Scaffolds.Settings(
    title = stringResource(id = R.string.CallLinkDetailsFragment__call_details),
    snackbarHost = {
      YouAreAlreadyInACallSnackbar(showAlreadyInACall)
      FailureSnackbar(failureSnackbar = state.failureSnackbar)
    },
    onNavigationClick = callback::onNavigationClicked,
    navigationIcon = if (showNavigationIcon) {
      ImageVector.vectorResource(id = R.drawable.symbol_arrow_start_24)
    } else {
      null
    }
  ) { paddingValues ->
    if (state.callLink == null) {
      return@Settings
    }

    LazyColumn(
      modifier = Modifier
        .padding(paddingValues)
        .fillMaxHeight()
    ) {
      item {
        SignalCallRow(
          callLink = state.callLink,
          callLinkPeekInfo = state.peekInfo,
          onJoinClicked = callback::onJoinClicked,
          modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
        )
      }

      if (state.callLink.credentials?.adminPassBytes != null) {
        item {
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
        }

        item {
          Rows.ToggleRow(
            checked = state.callLink.state.restrictions == Restrictions.ADMIN_APPROVAL,
            text = stringResource(id = R.string.CallLinkDetailsFragment__require_admin_approval),
            onCheckChanged = callback::onApproveAllMembersChanged,
            isLoading = state.isLoadingAdminApprovalChange
          )
        }

        item {
          Dividers.Default()
        }
      }

      item {
        Rows.TextRow(
          text = stringResource(id = R.string.CreateCallLinkBottomSheetDialogFragment__share_link_via_signal),
          icon = ImageVector.vectorResource(id = R.drawable.symbol_forward_24),
          onClick = callback::onShareLinkViaSignalClicked
        )
      }

      item {
        Rows.TextRow(
          text = stringResource(id = R.string.CreateCallLinkBottomSheetDialogFragment__copy_link),
          icon = ImageVector.vectorResource(id = R.drawable.symbol_copy_android_24),
          onClick = callback::onCopyClicked
        )
      }

      item {
        Rows.TextRow(
          text = stringResource(id = R.string.CallLinkDetailsFragment__share_link),
          icon = ImageVector.vectorResource(id = R.drawable.symbol_link_24),
          onClick = callback::onShareClicked
        )
      }

      item {
        Rows.TextRow(
          text = stringResource(id = R.string.CallLinkDetailsFragment__delete_call_link),
          icon = ImageVector.vectorResource(id = R.drawable.symbol_trash_24),
          foregroundTint = MaterialTheme.colorScheme.error,
          onClick = callback::onDeleteClicked
        )
      }
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

@Composable
private fun FailureSnackbar(
  failureSnackbar: CallLinkDetailsState.FailureSnackbar?,
  modifier: Modifier = Modifier
) {
  val message: String? = when (failureSnackbar) {
    CallLinkDetailsState.FailureSnackbar.COULD_NOT_DELETE_CALL_LINK -> stringResource(R.string.CallLinkDetailsFragment__couldnt_delete_call_link)
    CallLinkDetailsState.FailureSnackbar.COULD_NOT_SAVE_CHANGES -> stringResource(R.string.CallLinkDetailsFragment__couldnt_save_changes)
    CallLinkDetailsState.FailureSnackbar.COULD_NOT_UPDATE_ADMIN_APPROVAL -> stringResource(R.string.CallLinkDetailsFragment__couldnt_update_admin_approval)
    null -> null
  }

  val hostState = remember { SnackbarHostState() }
  Snackbars.Host(hostState, modifier = modifier)

  LaunchedEffect(message) {
    if (message != null) {
      hostState.showSnackbar(message)
    }
  }
}

@DayNightPreviews
@Composable
private fun CallLinkDetailsScreenPreview() {
  val callLink = remember {
    val credentials = CallLinkCredentials(
      byteArrayOf(1, 2, 3, 4),
      byteArrayOf(0, 1, 2, 3),
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

  SignalTheme {
    CallLinkDetailsScreen(
      CallLinkDetailsState(
        false,
        false,
        callLink
      ),
      true,
      CallLinkDetailsCallback.Empty
    )
  }
}
