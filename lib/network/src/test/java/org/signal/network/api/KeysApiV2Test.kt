/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.api

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.signal.libsignal.net.RequestResult
import org.signal.network.crypto.SealedSenderAccess

class KeysApiV2Test {

  private val identifier = "bbbbbbbb-0000-0000-0000-000000000002"
  private val realAccess = mockk<SealedSenderAccess.IndividualUnidentifiedAccessFirst>()

  private val success: RequestResult<KeysApiV2.PreKeyResponse, KeysApiV2.GetPreKeysError> = RequestResult.Success(KeysApiV2.PreKeyResponse(identityKey = ByteArray(0)))
  private val unauthorized: RequestResult<KeysApiV2.PreKeyResponse, KeysApiV2.GetPreKeysError> = RequestResult.NonSuccess(KeysApiV2.GetPreKeysError.Unauthorized(authenticated = false))
  private val notFound: RequestResult<KeysApiV2.PreKeyResponse, KeysApiV2.GetPreKeysError> = RequestResult.NonSuccess(KeysApiV2.GetPreKeysError.NotFound(authenticated = false))

  private val fetchedWith = mutableListOf<SealedSenderAccess?>()

  @Test
  fun `non-story access is used as is`() = runTest {
    val api = apiReturning(success)

    api.getPreKey(identifier, 1, realAccess)

    assertThat(fetchedWith).containsExactly(realAccess)
  }

  @Test
  fun `story without a prekey access fetches authenticated`() = runTest {
    val api = apiReturning(success)

    val result = api.getPreKey(identifier, 1, SealedSenderAccess.Story(mockk()))

    assertThat(result).isEqualTo(success)
    assertThat(fetchedWith).containsExactly(null)
  }

  @Test
  fun `story with a prekey access uses it`() = runTest {
    val api = apiReturning(success)

    val result = api.getPreKey(identifier, 1, SealedSenderAccess.Story(mockk(), preKeyAccess = realAccess))

    assertThat(result).isEqualTo(success)
    assertThat(fetchedWith).containsExactly(realAccess)
  }

  @Test
  fun `story with a rejected prekey access falls back to an authenticated fetch`() = runTest {
    val api = apiReturning(unauthorized, success)

    val result = api.getPreKey(identifier, 1, SealedSenderAccess.Story(mockk(), preKeyAccess = realAccess))

    assertThat(result).isEqualTo(success)
    assertThat(fetchedWith).containsExactly(realAccess, null)
  }

  @Test
  fun `story prekey access failure other than a rejection is not retried`() = runTest {
    val api = apiReturning(notFound)

    val result = api.getPreKey(identifier, 1, SealedSenderAccess.Story(mockk(), preKeyAccess = realAccess))

    assertThat(result).isEqualTo(notFound)
    assertThat(fetchedWith).containsExactly(realAccess)
  }

  private fun apiReturning(vararg results: RequestResult<KeysApiV2.PreKeyResponse, KeysApiV2.GetPreKeysError>): KeysApiV2 {
    val api = spyk(KeysApiV2(mockk(), mockk()))
    val remaining = ArrayDeque(results.toList())
    coEvery { api.requestPreKeys(any(), any(), any()) } coAnswers {
      fetchedWith += arg<SealedSenderAccess?>(2)
      remaining.removeFirst()
    }
    return api
  }
}
