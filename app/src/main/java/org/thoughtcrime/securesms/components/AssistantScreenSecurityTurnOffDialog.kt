/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import com.google.android.material.snackbar.Snackbar
import org.signal.core.ui.compose.ComposeDialogFragment
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.initializeScreenshotSecurity
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.snackbars.SnackbarState
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.main.MainNavigationViewModel
import org.thoughtcrime.securesms.main.MainSnackbarHostKey

/**
 * Confirms that the user wants to turn off screen security after it was automatically enabled by [AssistantScreenSecurityObserver].
 */
class AssistantScreenSecurityTurnOffDialog : ComposeDialogFragment() {

  companion object {
    private val TAG = Log.tag(AssistantScreenSecurityTurnOffDialog::class)
    private const val FRAGMENT_TAG = "AssistantScreenSecurityTurnOffDialog"

    fun show(fragmentManager: FragmentManager) {
      if (fragmentManager.findFragmentByTag(FRAGMENT_TAG) == null) {
        AssistantScreenSecurityTurnOffDialog().show(fragmentManager, FRAGMENT_TAG)
      }
    }
  }

  private val mainNavigationViewModel: MainNavigationViewModel by activityViewModels { MainNavigationViewModel.Factory() }

  @Composable
  override fun DialogContent() {
    AssistantScreenSecurityTurnOffDialogContent(
      onTurnOff = { turnOffScreenSecurity() },
      onDismiss = { dismissAllowingStateLoss() }
    )
  }

  private fun turnOffScreenSecurity() {
    Log.i(TAG, "User turned off screen security.")
    SignalStore.settings.isScreenSecurityEnabled = false
    SignalStore.settings.assistantScreenSecurityDeclinedAt = System.currentTimeMillis()

    val activity = requireActivity()
    activity.window.initializeScreenshotSecurity()

    val message = getString(R.string.AssistantScreenSecurityTurnOffDialog__screen_security_is_off)
    if (activity is MainActivity) {
      mainNavigationViewModel.snackbarRegistry.emit(
        SnackbarState(
          message = message,
          hostKey = MainSnackbarHostKey.Chat,
          fallbackKey = MainSnackbarHostKey.MainChrome
        )
      )
    } else {
      Snackbar.make(activity.findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT).show()
    }
  }
}

@Composable
private fun AssistantScreenSecurityTurnOffDialogContent(
  onTurnOff: () -> Unit = {},
  onDismiss: () -> Unit = {}
) {
  Dialogs.SimpleAlertDialog(
    title = stringResource(R.string.AssistantScreenSecurityTurnOffDialog__title),
    body = stringResource(R.string.AssistantScreenSecurityTurnOffDialog__body),
    confirm = stringResource(R.string.AssistantScreenSecurityTurnOffDialog__turn_off),
    dismiss = stringResource(android.R.string.cancel),
    confirmColor = MaterialTheme.colorScheme.error,
    onConfirm = onTurnOff,
    onDismiss = onDismiss
  )
}

@DayNightPreviews
@Composable
private fun AssistantScreenSecurityTurnOffDialogContentPreview() {
  Previews.Preview {
    AssistantScreenSecurityTurnOffDialogContent()
  }
}
