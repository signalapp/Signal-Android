/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.thoughtcrime.securesms.recipients.Recipient

/**
 * Objects that can be rendered into a string, including Recipient display names
 * and group info. This allows us to pass these objects through the view model without
 * having to pass around context.
 */
sealed interface CallString {
  @Composable
  fun renderToString(): String

  data class RecipientDisplayName(
    val recipient: Recipient
  ) : CallString {
    @Composable
    override fun renderToString(): String {
      return recipient.getDisplayName(LocalContext.current)
    }
  }

  data class ResourceString(
    @StringRes val resource: Int
  ) : CallString {
    @Composable
    override fun renderToString(): String {
      return stringResource(id = resource)
    }
  }
}
