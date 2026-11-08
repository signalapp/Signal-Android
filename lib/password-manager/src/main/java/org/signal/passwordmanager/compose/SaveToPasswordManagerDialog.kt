/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.passwordmanager.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.signal.passwordmanager.R
import org.signal.passwordmanager.SignalCredentialManager

private const val LEARN_MORE_URL = "https://support.signal.org/hc/articles/11275337387418/"

/**
 * Confirms the user wants to save to their password manager. When [isGooglePasswordManagerDefault] is true, the user is
 * told to enable on-device encryption first. Otherwise [trustBody] is shown, reminding them to pick one they trust.
 */
@Composable
fun SaveToPasswordManagerDialog(
  trustBody: String,
  isGooglePasswordManagerDefault: Boolean,
  onContinue: () -> Unit,
  onDismiss: () -> Unit
) {
  if (isGooglePasswordManagerDefault) {
    val context = LocalContext.current
    val passwordManagerIntent = remember { SignalCredentialManager.getGooglePasswordManagerIntent(context) }
    GooglePasswordManagerDialog(
      onContinue = onContinue,
      onDismiss = onDismiss,
      onOpenPasswordManager = passwordManagerIntent?.let { intent -> { context.startActivity(intent) } }
    )
  } else {
    Dialogs.SimpleAlertDialog(
      title = stringResource(R.string.SaveToPasswordManagerDialog__save_to_password_manager),
      body = trustBody,
      confirm = stringResource(R.string.SaveToPasswordManagerDialog__continue),
      dismiss = stringResource(android.R.string.cancel),
      onConfirm = onContinue,
      onDeny = onDismiss,
      onDismissRequest = onDismiss
    )
  }
}

@Composable
private fun GooglePasswordManagerDialog(
  onContinue: () -> Unit,
  onDismiss: () -> Unit,
  onOpenPasswordManager: (() -> Unit)?
) {
  val title = AnnotatedString(stringResource(R.string.SaveToPasswordManagerDialog__save_to_password_manager))
  val body = buildAnnotatedString {
    append(stringResource(R.string.SaveToPasswordManagerDialog__google_body))
    append(' ')
    withLink(LinkAnnotation.Url(url = LEARN_MORE_URL, styles = TextLinkStyles(style = SpanStyle(color = MaterialTheme.colorScheme.primary)))) {
      append(stringResource(R.string.SaveToPasswordManagerDialog__learn_more))
    }
  }
  val continueAnyway = AnnotatedString(stringResource(R.string.SaveToPasswordManagerDialog__continue_anyway))
  val cancel = AnnotatedString(stringResource(android.R.string.cancel))

  if (onOpenPasswordManager != null) {
    Dialogs.AdvancedAlertDialog(
      title = title,
      body = body,
      positive = AnnotatedString(stringResource(R.string.SaveToPasswordManagerDialog__open_google_password_manager)),
      neutral = continueAnyway,
      negative = cancel,
      onPositive = onOpenPasswordManager,
      onNeutral = onContinue,
      onNegative = onDismiss
    )
  } else {
    Dialogs.SimpleAlertDialog(
      title = title,
      body = body,
      confirm = continueAnyway,
      dismiss = cancel,
      onConfirm = onContinue,
      onDeny = onDismiss,
      onDismissRequest = onDismiss
    )
  }
}

@DayNightPreviews
@Composable
private fun SaveToPasswordManagerDialogPreview() {
  Previews.Preview {
    SaveToPasswordManagerDialog(
      trustBody = "Only store your credentials in a password manager that you trust is secure.",
      isGooglePasswordManagerDefault = false,
      onContinue = {},
      onDismiss = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun GooglePasswordManagerDialogPreview() {
  Previews.Preview {
    GooglePasswordManagerDialog(
      onContinue = {},
      onDismiss = {},
      onOpenPasswordManager = {}
    )
  }
}
