/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.contactsupport

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalPreview
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.SupportEmailUtil

interface ContactSupportCallbacks {
  fun submitWithDebuglog()
  fun submitWithoutDebuglog()
  fun cancel()

  object Empty : ContactSupportCallbacks {
    override fun submitWithDebuglog() = Unit
    override fun submitWithoutDebuglog() = Unit
    override fun cancel() = Unit
  }

  fun interface StringForReason<Reason> {
    @StringRes
    operator fun invoke(reason: Reason?): Int
  }
}

/**
 * Three-option contact support dialog.
 */
@Composable
fun ContactSupportDialog(
  showInProgress: Boolean,
  callbacks: ContactSupportCallbacks
) {
  if (showInProgress) {
    Dialogs.IndeterminateProgressDialog()
  } else {
    Dialogs.AdvancedAlertDialog(
      title = stringResource(R.string.ContactSupportDialog_submit_debug_log),
      body = stringResource(R.string.ContactSupportDialog_your_debug_logs),
      positive = stringResource(R.string.ContactSupportDialog_submit_with_debug),
      onPositive = { callbacks.submitWithDebuglog() },
      neutral = stringResource(R.string.ContactSupportDialog_submit_without_debug),
      onNeutral = { callbacks.submitWithoutDebuglog() },
      negative = stringResource(android.R.string.cancel),
      onNegative = { callbacks.cancel() }
    )
  }
}

/**
 * Used in conjunction with [ContactSupportDialog] and [ContactSupportViewModel] to trigger
 * sending an email when ready.
 */
@Composable
fun <Reason> SendSupportEmailEffect(
  contactSupportState: ContactSupportViewModel.ContactSupportState<Reason>,
  subjectRes: ContactSupportCallbacks.StringForReason<Reason>,
  filterRes: ContactSupportCallbacks.StringForReason<Reason>,
  hide: () -> Unit
) {
  val context = LocalContext.current
  LaunchedEffect(contactSupportState.sendEmail) {
    if (contactSupportState.sendEmail) {
      val subject = context.getString(subjectRes(contactSupportState.reason))
      val prefix = if (contactSupportState.debugLogUrl != null) {
        "\n${context.getString(R.string.HelpFragment__debug_log)} ${contactSupportState.debugLogUrl}\n\n"
      } else {
        ""
      }

      val body = SupportEmailUtil.generateSupportEmailBody(context, filterRes(contactSupportState.reason), prefix, null)
      CommunicationActions.openEmail(context, SupportEmailUtil.getSupportEmailAddress(context), subject, body)
      hide()
    }
  }
}

@SignalPreview
@Composable
private fun ContactSupportDialogPreview() {
  Previews.Preview {
    ContactSupportDialog(
      false,
      ContactSupportCallbacks.Empty
    )
  }
}
