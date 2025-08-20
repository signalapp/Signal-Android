/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.recipients

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.rx3.asFlow
import kotlinx.coroutines.withContext
import org.signal.core.util.concurrent.SignalDispatchers

/**
 * Allows composable UI to subscribe to changes for a specific field on a recipient.
 */
@Composable
fun <T> rememberRecipientField(recipient: Recipient, toField: Recipient.() -> T): State<T> {
  return rememberRecipientField(recipientId = recipient.id, initialData = recipient, toField = toField)
}

/**
 * Allows composable UI to subscribe to changes for a specific field on a recipient.
 */
@Composable
fun <T> rememberRecipientField(recipientId: RecipientId, toField: Recipient.() -> T): State<T> {
  return rememberRecipientField(recipientId = recipientId, initialData = Recipient.UNKNOWN, toField = toField)
}

@Composable
private fun <T> rememberRecipientField(recipientId: RecipientId, initialData: Recipient, toField: Recipient.() -> T): State<T> {
  var recipientAndCounter by remember(recipientId) { mutableStateOf(initialData to 0L) }

  LaunchedEffect(recipientId) {
    withContext(SignalDispatchers.IO) {
      Recipient.observable(recipientId)
        .asFlow()
        .collect {
          recipientAndCounter = it to (recipientAndCounter.second + 1L)
        }
    }
  }

  return remember(recipientAndCounter) {
    derivedStateOf { toField(recipientAndCounter.first) }
  }
}
