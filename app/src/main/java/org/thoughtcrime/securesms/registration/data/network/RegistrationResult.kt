/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.data.network

import org.signal.core.util.logging.Log

/**
 * This is a merging of the NetworkResult pattern and the Processor pattern of registration v1.
 * The goal is to enumerate all possible responses as sealed classes, which means the consumer will be able to handle them in an exhaustive when clause
 *
 * @property errorCause the [Throwable] that caused the Error. Null if the network request was successful.
 *
 */
abstract class RegistrationResult(private val errorCause: Throwable?) {
  fun isSuccess(): Boolean {
    return errorCause == null
  }

  fun getCause(): Throwable {
    if (errorCause == null) {
      throw IllegalStateException("Cannot get cause from successful processor!")
    }

    return errorCause
  }

  fun logCause() {
    errorCause?.let {
      Log.w(Log.tag(this::class), "Cause:", it)
    }
  }
}
