/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.util

import org.signal.libsignal.internal.CompletableFuture

/**
 * A Kotlin friendly adapter for [org.signal.libsignal.internal.CompletableFuture.whenComplete]
 * taking two callbacks ([onSuccess] and [onFailure]) instead of a [java.util.function.BiConsumer].
 */
fun <T> CompletableFuture<T>.whenComplete(
  onSuccess: ((T?) -> Unit),
  onFailure: ((Throwable) -> Unit)
): CompletableFuture<T> {
  return this.whenComplete { value, throwable ->
    if (throwable != null) {
      onFailure(throwable)
    } else {
      onSuccess(value)
    }
  }
}
