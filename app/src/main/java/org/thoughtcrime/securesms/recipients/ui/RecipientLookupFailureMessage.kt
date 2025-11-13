/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.recipients.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import org.signal.core.ui.compose.Dialogs
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.recipients.RecipientRepository
import org.thoughtcrime.securesms.recipients.RecipientRepository.IdLookupResult
import org.thoughtcrime.securesms.recipients.RecipientRepository.PhoneLookupResult

/**
 * Handles displaying a message to the user when a recipient lookup fails.
 */
@Composable
fun RecipientLookupFailureMessage(
  failure: RecipientRepository.LookupResult.Failure,
  onDismissed: () -> Unit
) {
  val context: Context = LocalContext.current

  when (failure) {
    is PhoneLookupResult.NotFound,
    is PhoneLookupResult.InvalidPhone -> {
      val phoneDisplayText: String = when (failure) {
        is PhoneLookupResult.NotFound -> failure.phone.displayText
        is PhoneLookupResult.InvalidPhone -> failure.invalidValue
      }

      Dialogs.SimpleMessageDialog(
        message = stringResource(R.string.RecipientLookup_error__s_is_not_a_signal_user, phoneDisplayText),
        dismiss = stringResource(android.R.string.ok),
        onDismiss = { onDismissed() }
      )
    }

    is IdLookupResult.FoundSome -> Dialogs.SimpleMessageDialog(
      message = pluralStringResource(
        id = R.plurals.RecipientLookup_error__not_signal_users,
        count = failure.notFound.size,
        failure.notFound.joinToString(", ") { it.getDisplayName(context) }
      ),
      dismiss = stringResource(android.R.string.ok),
      onDismiss = { onDismissed() }
    )

    is RecipientRepository.LookupResult.NetworkError -> Dialogs.SimpleMessageDialog(
      message = stringResource(R.string.NetworkFailure__network_error_check_your_connection_and_try_again),
      dismiss = stringResource(android.R.string.ok),
      onDismiss = { onDismissed() }
    )
  }
}
