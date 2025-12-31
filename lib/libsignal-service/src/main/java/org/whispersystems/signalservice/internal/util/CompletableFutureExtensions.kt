/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.util

import org.signal.libsignal.internal.CompletableFuture

/**
 * A Kotlin friendly adapter for [org.signal.libsignal.internal.CompletableFuture.whenComplete]
 * taking two callbacks ([onSuccess] and [onFailure]) instead of a [java.util.function.BiConsumer].
 *
 * Note that for libsignal's implementation of CompletableFuture, whenComplete will complete handlers in
 * the order they are enqueued. This is a stronger guarantee than is given by the standard Java specification
 * and is actively used by clients (e.g. LibSignalChatConnection) to reduce boilerplate in handling race conditions.
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
