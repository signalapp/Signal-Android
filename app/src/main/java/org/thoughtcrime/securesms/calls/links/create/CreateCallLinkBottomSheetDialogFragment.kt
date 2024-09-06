/**
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

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
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ShareCompat
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.signal.core.ui.BottomSheets
import org.signal.core.ui.Buttons
import org.signal.core.ui.Dividers
import org.signal.core.ui.Rows
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.core.util.logging.Log
import org.signal.ringrtc.CallLinkState
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.calls.links.CallLinks
import org.thoughtcrime.securesms.calls.links.EditCallLinkNameDialogFragment
import org.thoughtcrime.securesms.calls.links.SignalCallRow
import org.thoughtcrime.securesms.compose.ComposeBottomSheetDialogFragment
import org.thoughtcrime.securesms.database.CallLinkTable
import org.thoughtcrime.securesms.service.webrtc.links.CreateCallLinkResult
import org.thoughtcrime.securesms.service.webrtc.links.UpdateCallLinkResult
import org.thoughtcrime.securesms.sharing.v2.ShareActivity
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.Util

/**
 * Bottom sheet for creating call links
 */
class CreateCallLinkBottomSheetDialogFragment : ComposeBottomSheetDialogFragment() {

  companion object {
    private val TAG = Log.tag(CreateCallLinkBottomSheetDialogFragment::class.java)
  }

  private val viewModel: CreateCallLinkViewModel by viewModels()
  private val lifecycleDisposable = LifecycleDisposable()

  override val peekHeightPercentage: Float = 1f

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    lifecycleDisposable.bindTo(viewLifecycleOwner)
    parentFragmentManager.setFragmentResultListener(EditCallLinkNameDialogFragment.RESULT_KEY, viewLifecycleOwner) { resultKey, bundle ->
      if (bundle.containsKey(resultKey)) {
        setCallName(bundle.getString(resultKey)!!)
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

      BottomSheets.Handle(modifier = Modifier.align(Alignment.CenterHorizontally))

      Spacer(modifier = Modifier.height(20.dp))

      Text(
        text = stringResource(id = R.string.CreateCallLinkBottomSheetDialogFragment__create_call_link),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
      )

      Spacer(modifier = Modifier.height(24.dp))

      SignalCallRow(
        callLink = callLink,
        callLinkPeekInfo = null,
        onJoinClicked = this@CreateCallLinkBottomSheetDialogFragment::onJoinClicked
      )

      Spacer(modifier = Modifier.height(12.dp))

      Rows.TextRow(
        text = stringResource(
          id = if (callLink.state.name.isEmpty()) {
            R.string.CreateCallLinkBottomSheetDialogFragment__add_call_name
          } else {
            R.string.CreateCallLinkBottomSheetDialogFragment__edit_call_name
          }
        ),
        onClick = this@CreateCallLinkBottomSheetDialogFragment::onAddACallNameClicked
      )

      Rows.ToggleRow(
        checked = callLink.state.restrictions == CallLinkState.Restrictions.ADMIN_APPROVAL,
        text = stringResource(id = R.string.CreateCallLinkBottomSheetDialogFragment__require_admin_approval),
        onCheckChanged = this@CreateCallLinkBottomSheetDialogFragment::setApproveAllMembers,
        modifier = Modifier.clickable(onClick = this@CreateCallLinkBottomSheetDialogFragment::toggleApproveAllMembers)
      )

      Dividers.Default()

      Rows.TextRow(
        text = stringResource(id = R.string.CreateCallLinkBottomSheetDialogFragment__share_link_via_signal),
        icon = painterResource(id = R.drawable.symbol_forward_24),
        onClick = this@CreateCallLinkBottomSheetDialogFragment::onShareViaSignalClicked
      )

      Rows.TextRow(
        text = stringResource(id = R.string.CreateCallLinkBottomSheetDialogFragment__copy_link),
        icon = painterResource(id = R.drawable.symbol_copy_android_24),
        onClick = this@CreateCallLinkBottomSheetDialogFragment::onCopyLinkClicked
      )

      Rows.TextRow(
        text = stringResource(id = R.string.CreateCallLinkBottomSheetDialogFragment__share_link),
        icon = painterResource(id = R.drawable.symbol_share_android_24),
        onClick = this@CreateCallLinkBottomSheetDialogFragment::onShareLinkClicked
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

  private fun setCallName(callName: String) {
    lifecycleDisposable += viewModel.setCallName(callName).subscribeBy(onSuccess = {
      if (it !is UpdateCallLinkResult.Update) {
        Log.w(TAG, "Failed to update call link name")
        toastFailure()
      }
    }, onError = this::handleError)
  }

  private fun setApproveAllMembers(approveAllMembers: Boolean) {
    lifecycleDisposable += viewModel.setApproveAllMembers(approveAllMembers).subscribeBy(onSuccess = {
      if (it !is UpdateCallLinkResult.Update) {
        Log.w(TAG, "Failed to update call link restrictions")
        toastFailure()
      }
    }, onError = this::handleError)
  }

  private fun toggleApproveAllMembers() {
    lifecycleDisposable += viewModel.toggleApproveAllMembers().subscribeBy(onSuccess = {
      if (it !is UpdateCallLinkResult.Update) {
        Log.w(TAG, "Failed to update call link restrictions")
        toastFailure()
      }
    }, onError = this::handleError)
  }

  private fun onAddACallNameClicked() {
    val snapshot = viewModel.callLink.value
    findNavController().navigate(
      CreateCallLinkBottomSheetDialogFragmentDirections.actionCreateCallLinkBottomSheetToEditCallLinkNameDialogFragment(snapshot.state.name)
    )
  }

  private fun onJoinClicked() {
    lifecycleDisposable += viewModel.commitCallLink().subscribeBy(onSuccess = {
      when (it) {
        is EnsureCallLinkCreatedResult.Success -> {
          CommunicationActions.startVideoCall(requireActivity(), it.recipient)
          dismissAllowingStateLoss()
        }

        is EnsureCallLinkCreatedResult.Failure -> handleCreateCallLinkFailure(it.failure)
      }
    }, onError = this::handleError)
  }

  private fun onDoneClicked() {
    lifecycleDisposable += viewModel.commitCallLink().subscribeBy(onSuccess = {
      when (it) {
        is EnsureCallLinkCreatedResult.Success -> dismissAllowingStateLoss()
        is EnsureCallLinkCreatedResult.Failure -> handleCreateCallLinkFailure(it.failure)
      }
    }, onError = this::handleError)
  }

  private fun onShareViaSignalClicked() {
    lifecycleDisposable += viewModel.commitCallLink().subscribeBy(onSuccess = {
      when (it) {
        is EnsureCallLinkCreatedResult.Success -> {
          startActivity(
            ShareActivity.sendSimpleText(
              requireContext(),
              getString(R.string.CreateCallLink__use_this_link_to_join_a_signal_call, CallLinks.url(viewModel.linkKeyBytes))
            )
          )
        }

        is EnsureCallLinkCreatedResult.Failure -> handleCreateCallLinkFailure(it.failure)
      }
    }, onError = this::handleError)
  }

  private fun onCopyLinkClicked() {
    lifecycleDisposable += viewModel.commitCallLink().subscribeBy(onSuccess = {
      when (it) {
        is EnsureCallLinkCreatedResult.Success -> {
          Util.copyToClipboard(requireContext(), CallLinks.url(viewModel.linkKeyBytes))
          Toast.makeText(requireContext(), R.string.CreateCallLinkBottomSheetDialogFragment__copied_to_clipboard, Toast.LENGTH_LONG).show()
        }

        is EnsureCallLinkCreatedResult.Failure -> handleCreateCallLinkFailure(it.failure)
      }
    }, onError = this::handleError)
  }

  private fun onShareLinkClicked() {
    lifecycleDisposable += viewModel.commitCallLink().subscribeBy {
      when (it) {
        is EnsureCallLinkCreatedResult.Success -> {
          val mimeType = Intent.normalizeMimeType("text/plain")
          val shareIntent = ShareCompat.IntentBuilder(requireContext())
            .setText(CallLinks.url(viewModel.linkKeyBytes))
            .setType(mimeType)
            .createChooserIntent()

          try {
            startActivity(shareIntent)
          } catch (e: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.CreateCallLinkBottomSheetDialogFragment__failed_to_open_share_sheet, Toast.LENGTH_LONG).show()
          }
        }

        is EnsureCallLinkCreatedResult.Failure -> {
          Log.w(TAG, "Failed to create link: $it")
          toastFailure()
        }
      }
    }
  }

  private fun handleCreateCallLinkFailure(failure: CreateCallLinkResult.Failure) {
    Log.w(TAG, "Failed to create call link: $failure")
    toastFailure()
  }

  private fun handleError(throwable: Throwable) {
    Log.w(TAG, "Failed to create call link.", throwable)
    toastFailure()
  }

  private fun toastFailure() {
    Toast.makeText(requireContext(), R.string.CallLinkDetailsFragment__couldnt_save_changes, Toast.LENGTH_LONG).show()
  }
}
