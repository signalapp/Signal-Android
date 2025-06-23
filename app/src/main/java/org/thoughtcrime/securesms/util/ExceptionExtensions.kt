/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

@file:JvmName("ExceptionHelper")

package org.thoughtcrime.securesms.util

import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException
import java.io.IOException

/**
 * Returns true if this exception is a retryable I/O Exception. Helpful for jobs.
 */
fun Throwable.isRetryableIOException(): Boolean {
  return this is IOException && this !is NonSuccessfulResponseCodeException
}
