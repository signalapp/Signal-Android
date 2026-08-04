/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import org.signal.core.ui.compose.SignalIcons

/**
 * A brief confirmation of something the user just did, shown over the middle of the screen rather than as a snackbar so
 * that it does not sit on top of the controls it is reporting on.
 */
internal data class ToastEvent(
  val icon: SignalIcons,
  val message: ToastMessage
)

/**
 * The copy for a [ToastEvent], which is either a plain string or one counting the items it applies to.
 */
internal sealed interface ToastMessage {
  data class Text(@get:StringRes val id: Int) : ToastMessage
  data class Quantity(@get:PluralsRes val id: Int, val count: Int) : ToastMessage
}
