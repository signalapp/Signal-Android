/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.shared

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.signal.core.ui.compose.LocalChatColorProvider
import org.signal.mediasend.MediaRecipientId

/**
 * The chat color of the one recipient this send is headed to, which the flow's chrome tints itself with so a send looks
 * like the conversation it is going to.
 *
 * Null when there is no single conversation to take a color from: a story, or a flow that has yet to pick a destination.
 * Callers fall back to the theme rather than to a color of their own.
 */
@Composable
internal fun chatColorFor(recipientId: MediaRecipientId?): Color? {
  return recipientId?.let { LocalChatColorProvider.current(it.id).value }
}
