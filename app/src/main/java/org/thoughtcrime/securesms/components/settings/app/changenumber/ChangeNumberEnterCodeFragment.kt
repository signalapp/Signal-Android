/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.changenumber

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.changenumber.ChangeNumberUtil.changeNumberSuccess
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.registration.sms.ReceivedSmsEvent
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Screen used to enter the registration code provided by the service.
 */
class ChangeNumberEnterCodeFragment : LoggingFragment(R.layout.fragment_change_number_enter_code) {

  companion object {
    private val TAG: String = Log.tag(ChangeNumberEnterCodeFragment::class.java)
    private const val BOTTOM_SHEET_TAG = "support_bottom_sheet"
  }

  private val viewModel by activityViewModels<ChangeNumberViewModel>()
  private var autopilotCodeEntryActive = false

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    val composeView = view.findViewById<ComposeView>(R.id.compose_root)
    composeView.setContent {
      ChangeNumberEnterCodeScreen(
        viewModel = viewModel,
        onNavigateUp = { navigateUp() },
        onAccountLocked = { findNavController().safeNavigate(ChangeNumberEnterCodeFragmentDirections.actionChangeNumberEnterCodeFragmentToChangeNumberAccountLocked()) },
        onRegistrationLocked = { timeRemaining -> findNavController().safeNavigate(ChangeNumberEnterCodeFragmentDirections.actionChangeNumberEnterCodeFragmentToChangeNumberRegistrationLock(timeRemaining)) },
        onSuccess = { changeNumberSuccess() },
        showErrorDialog = { title, message, onOk -> showErrorDialog(title, message, onOk) }
      )
    }
  }

  private fun navigateUp() {
    if (SignalStore.misc.isChangeNumberLocked) {
      startActivity(ChangeNumberLockActivity.createIntent(requireContext()))
    } else {
      findNavController().navigateUp()
    }
  }

  private fun showErrorDialog(title: String?, message: String, onOk: (() -> Unit)?) {
    MaterialAlertDialogBuilder(requireContext()).apply {
      if (title != null) setTitle(title)
      setMessage(message)
      setPositiveButton(android.R.string.ok) { _, _ -> onOk?.invoke() }
      show()
    }
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun onVerificationCodeReceived(event: ReceivedSmsEvent) {
    viewModel.autofillCode(event.code)
    // Compose will react to state change
  }
}

@Composable
fun ChangeNumberEnterCodeScreen(
  viewModel: ChangeNumberViewModel,
  onNavigateUp: () -> Unit,
  onAccountLocked: () -> Unit,
  onRegistrationLocked: (Long) -> Unit,
  onSuccess: () -> Unit,
  showErrorDialog: (String?, String, (() -> Unit)?) -> Unit
) {
  // TODO: Implement the Compose UI, state, and error handling here.
}
