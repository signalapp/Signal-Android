/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameInstanceAs
import org.junit.Test
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.net.RequestUnauthorizedException
import org.signal.libsignal.net.RetryLaterException
import java.net.SocketException
import java.time.Duration

class RequestResultClassificationTest {

  @Test
  fun `plain IOException is retryable`() {
    val exception = SocketException("no connection")

    val result = exception.toRequestResult<RequestUnauthorizedException>()

    assertThat(result).isInstanceOf(RequestResult.RetryableNetworkError::class)
    assertThat((result as RequestResult.RetryableNetworkError).networkError).isSameInstanceAs(exception)
  }

  @Test
  fun `non-IOException is an application error`() {
    val exception = IllegalStateException("bug")

    val result = exception.toRequestResult<RequestUnauthorizedException>()

    assertThat(result).isInstanceOf(RequestResult.ApplicationError::class)
    assertThat((result as RequestResult.ApplicationError).cause).isSameInstanceAs(exception)
  }

  @Test
  fun `libsignal classification still wins over the IOException fallback`() {
    val exception = RetryLaterException(Duration.ofSeconds(30))

    val result = exception.toRequestResult<RequestUnauthorizedException>()

    assertThat(result).isInstanceOf(RequestResult.RetryableNetworkError::class)
    assertThat((result as RequestResult.RetryableNetworkError).retryAfter).isEqualTo(Duration.ofSeconds(30))
  }

  @Test
  fun `expected error is a non-success even though it is an IOException`() {
    val exception = RequestUnauthorizedException("nope")

    val result = exception.toRequestResult<RequestUnauthorizedException>()

    assertThat(result).isInstanceOf(RequestResult.NonSuccess::class)
    assertThat((result as RequestResult.NonSuccess).error).isSameInstanceAs(exception)
  }
}
