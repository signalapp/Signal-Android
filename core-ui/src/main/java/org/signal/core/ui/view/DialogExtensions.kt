/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.view

import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Shows a dialog and suspends until user interaction, returning the [AlertDialogResult].
 *
 * Note: this method will overwrite any existing dialog button click listeners or cancellation listener.
 */
suspend fun AlertDialog.awaitResult(
  positiveButtonTextId: Int? = null,
  negativeButtonTextId: Int? = null,
  neutralButtonTextId: Int? = null
) = awaitResult(
  positiveButtonText = positiveButtonTextId?.let(context::getString),
  negativeButtonText = negativeButtonTextId?.let(context::getString),
  neutralButtonText = neutralButtonTextId?.let(context::getString)
)

/**
 * Shows a dialog and suspends until user interaction, returning the [AlertDialogResult].
 *
 * Note: this method will overwrite any existing dialog button click listeners or cancellation listener.
 */
suspend fun AlertDialog.awaitResult(
  positiveButtonText: String? = null,
  negativeButtonText: String? = null,
  neutralButtonText: String? = null
) = suspendCancellableCoroutine { continuation ->

  positiveButtonText?.let { text -> setButton(AlertDialog.BUTTON_POSITIVE, text) { _, _ -> continuation.resume(AlertDialogResult.POSITIVE) } }
  negativeButtonText?.let { text -> setButton(AlertDialog.BUTTON_NEGATIVE, text) { _, _ -> continuation.resume(AlertDialogResult.NEGATIVE) } }
  neutralButtonText?.let { text -> setButton(AlertDialog.BUTTON_NEUTRAL, text) { _, _ -> continuation.resume(AlertDialogResult.NEUTRAL) } }

  setOnCancelListener { continuation.resume(AlertDialogResult.CANCELED) }
  continuation.invokeOnCancellation { dismiss() }

  show()
}

enum class AlertDialogResult {
  POSITIVE,
  NEGATIVE,
  NEUTRAL,
  CANCELED
}
