/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException

class NetworkResultTest {
  @Test
  fun `generic success`() {
    val result = NetworkResult.fromFetch {}

    assertTrue(result is NetworkResult.Success)
  }

  @Test
  fun `generic non-successful status code`() {
    val exception = NonSuccessfulResponseCodeException(404, "not found", "body")

    val result = NetworkResult.fromFetch {
      throw exception
    }

    check(result is NetworkResult.StatusCodeError)
    assertEquals(exception, result.exception)
    assertEquals(404, result.code)
    assertEquals("body", result.body)
  }

  @Test
  fun `generic network error`() {
    val exception = PushNetworkException("general exception")

    val result = NetworkResult.fromFetch {
      throw exception
    }

    assertTrue(result is NetworkResult.NetworkError)
    assertEquals(exception, (result as NetworkResult.NetworkError).exception)
  }

  @Test
  fun `generic application error`() {
    val throwable = RuntimeException("test")

    val result = NetworkResult.fromFetch {
      throw throwable
    }

    assertTrue(result is NetworkResult.ApplicationError)
    assertEquals(throwable, (result as NetworkResult.ApplicationError).throwable)
  }

  @Test
  fun `then - generic`() {
    val result = NetworkResult
      .fromFetch { NetworkResult.Success(1) }
      .then { NetworkResult.Success(2) }

    assertTrue(result is NetworkResult.Success)
    assertEquals(2, (result as NetworkResult.Success).result)
  }

  @Test
  fun `then - doesn't run on error`() {
    val throwable = RuntimeException("test")
    var run = false

    val result = NetworkResult
      .fromFetch { throw throwable }
      .then {
        run = true
        NetworkResult.Success(1)
      }

    assertTrue(result is NetworkResult.ApplicationError)
    assertFalse(run)
  }

  @Test
  fun `map - generic`() {
    val result = NetworkResult
      .fromFetch { NetworkResult.Success(1) }
      .map { 2 }

    assertTrue(result is NetworkResult.Success)
    assertEquals(2, (result as NetworkResult.Success).result)
  }

  @Test
  fun `map - doesn't run on error`() {
    val throwable = RuntimeException("test")
    var run = false

    val result = NetworkResult
      .fromFetch { throw throwable }
      .map {
        run = true
        1
      }

    assertTrue(result is NetworkResult.ApplicationError)
    assertFalse(run)
  }

  @Test
  fun `runIfSuccessful - doesn't run on error`() {
    val throwable = RuntimeException("test")
    var run = false

    val result = NetworkResult
      .fromFetch { throw throwable }
      .runIfSuccessful { run = true }

    assertTrue(result is NetworkResult.ApplicationError)
    assertFalse(run)
  }

  @Test
  fun `runIfSuccessful - runs on success`() {
    var run = false

    NetworkResult
      .fromFetch { NetworkResult.Success(1) }
      .runIfSuccessful { run = true }

    assertTrue(run)
  }

  @Test
  fun `runIfSuccessful - runs before error`() {
    val throwable = RuntimeException("test")
    var run = false

    val result = NetworkResult
      .fromFetch { NetworkResult.Success(Unit) }
      .runIfSuccessful { run = true }
      .then { NetworkResult.ApplicationError<Unit>(throwable) }

    assertTrue(result is NetworkResult.ApplicationError)
    assertTrue(run)
  }

  @Test
  fun `runOnStatusCodeError - simple call`() {
    var handled = false

    NetworkResult
      .fromFetch { throw NonSuccessfulResponseCodeException(404, "not found", "body") }
      .runOnStatusCodeError { handled = true }

    assertTrue(handled)
  }

  @Test
  fun `runOnStatusCodeError - ensure only called once`() {
    var handleCount = 0

    NetworkResult
      .fromFetch { throw NonSuccessfulResponseCodeException(404, "not found", "body") }
      .runOnStatusCodeError { handleCount++ }
      .map { 1 }
      .then { NetworkResult.Success(2) }
      .map { 3 }

    assertEquals(1, handleCount)
  }

  @Test
  fun `runOnStatusCodeError - called when placed before a failing then`() {
    var handled = false

    val result = NetworkResult
      .fromFetch { }
      .runOnStatusCodeError { handled = true }
      .then { NetworkResult.fromFetch { throw NonSuccessfulResponseCodeException(404, "not found", "body") } }

    assertTrue(handled)
    assertTrue(result is NetworkResult.StatusCodeError)
  }

  @Test
  fun `runOnStatusCodeError - called when placed two spots before a failing then`() {
    var handled = false

    val result = NetworkResult
      .fromFetch { }
      .runOnStatusCodeError { handled = true }
      .then { NetworkResult.Success(Unit) }
      .then { NetworkResult.fromFetch { throw NonSuccessfulResponseCodeException(404, "not found", "body") } }

    assertTrue(handled)
    assertTrue(result is NetworkResult.StatusCodeError)
  }

  @Test
  fun `runOnStatusCodeError - should not be called for successful results`() {
    var handled = false

    NetworkResult
      .fromFetch {}
      .runOnStatusCodeError { handled = true }

    NetworkResult
      .fromFetch { throw RuntimeException("application error") }
      .runOnStatusCodeError { handled = true }

    NetworkResult
      .fromFetch { throw PushNetworkException("network error") }
      .runOnStatusCodeError { handled = true }

    assertFalse(handled)
  }
}
