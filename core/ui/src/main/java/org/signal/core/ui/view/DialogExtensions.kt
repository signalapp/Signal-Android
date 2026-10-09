/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.view

import android.app.Dialog
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
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

fun Dialog.dismissOnDestroy(lifecycle: Lifecycle) {
  if (lifecycle.currentState == Lifecycle.State.DESTROYED) {
    dismiss()
    return
  }

  val observer = object : DefaultLifecycleObserver {
    override fun onDestroy(owner: LifecycleOwner) {
      dismiss()
    }
  }

  lifecycle.addObserver(observer)

  // Detach instead of setOnDismissListener so a caller's own dismiss listener is not overwritten
  window?.decorView?.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
    override fun onViewAttachedToWindow(view: View) = Unit

    override fun onViewDetachedFromWindow(view: View) {
      view.removeOnAttachStateChangeListener(this)
      lifecycle.removeObserver(observer)
    }
  })
}

enum class AlertDialogResult {
  POSITIVE,
  NEGATIVE,
  NEUTRAL,
  CANCELED
}
