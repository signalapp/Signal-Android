/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

@file:JvmName("JvmRxExtensions")

package org.signal.core.util.concurrent

import io.reactivex.rxjava3.core.Single

/**
 * Throw an [InterruptedException] if a [Single.blockingGet] call is interrupted. This can
 * happen when being called by code already within an Rx chain that is disposed.
 *
 * [Single.blockingGet] is considered harmful and should not be used.
 */
@Throws(InterruptedException::class)
fun <T : Any> Single<T>.safeBlockingGet(): T {
  try {
    return blockingGet()
  } catch (e: RuntimeException) {
    val cause = e.cause
    if (cause is InterruptedException) {
      throw cause
    } else {
      throw e
    }
  }
}
