/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.warning

import android.content.DialogInterface
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.setFragmentResult
import org.signal.core.ui.compose.ComposeBottomSheetDialogFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.v2.ui.warning.RecoveryKeyPasteWarningFragment.Companion.REQUEST_KEY
import org.thoughtcrime.securesms.util.CommunicationActions

/**
 * Displayed via the [org.thoughtcrime.securesms.components.settings.conversation.ConversationSettingsFragment] whenever the user
 * attempts to paste their recovery key into the input field.
 *
 * A result is always delivered to [REQUEST_KEY] when this fragment is dismissed, with the boolean
 * indicating whether the user chose to proceed with the paste. The host can rely on this firing for
 * every dismissal path (paste, decline, or cancel) to restore its own state.
 */
class RecoveryKeyPasteWarningFragment : ComposeBottomSheetDialogFragment() {

  companion object {
    const val REQUEST_KEY = "recovery_key_request"
  }

  private var shouldPaste = false

  override fun onDismiss(dialog: DialogInterface) {
    setFragmentResult(
      REQUEST_KEY,
      Bundle().apply {
        putBoolean(REQUEST_KEY, shouldPaste)
      }
    )

    super.onDismiss(dialog)
  }

  @Composable
  override fun SheetContent() {
    val context = LocalContext.current
    val url = stringResource(R.string.recovery_key_phishing_support_url)

    val eventHandler: (RecoveryKeyWarningSheetEvent) -> Unit = {
      when (it) {
        RecoveryKeyWarningSheetEvent.DoNotShareClick -> {
          dismissAllowingStateLoss()
        }

        RecoveryKeyWarningSheetEvent.GotItClick -> {
          error("Not supported for paste")
        }

        RecoveryKeyWarningSheetEvent.LearnMoreClick -> {
          CommunicationActions.openBrowserLink(context, url)
          dismissAllowingStateLoss()
        }

        RecoveryKeyWarningSheetEvent.ShareKeyClick -> {
          shouldPaste = true
          dismissAllowingStateLoss()
        }
      }
    }

    RecoveryKeyWarningSheetContent(
      clipStage = ClipStage.PASTE,
      events = eventHandler
    )
  }
}
