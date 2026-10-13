/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.api

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.signal.core.models.ServiceId
import org.signal.libsignal.net.MismatchedDeviceException
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.net.RequestUnauthorizedException
import org.signal.libsignal.net.SealedSendFailure
import org.signal.libsignal.net.UserBasedAuthorization
import org.signal.libsignal.net.UserBasedSendAuthorization
import org.signal.libsignal.zkgroup.groupsend.GroupSendFullToken
import org.whispersystems.signalservice.api.crypto.SealedSenderAccess
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess
import java.util.UUID

class MessageApiV2Test {

  private val recipient = ServiceId.ACI.from(UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002"))
  private val accessKey = ByteArray(16) { 1 }
  private val token = mockk<GroupSendFullToken>()

  private val success: RequestResult<Unit, SealedSendFailure> = RequestResult.Success(Unit)
  private val unauthorized: RequestResult<Unit, SealedSendFailure> = RequestResult.NonSuccess(RequestUnauthorizedException("bad access"))

  private val sentWith = mutableListOf<UserBasedSendAuthorization>()

  @Test
  fun `non-zero access key uses AccessKey auth`() = runTest {
    val api = apiReturning(success)

    api.sendSealedSenderMessage(recipient, 1L, emptyList(), individualUnidentifiedAccessFirst(accessKey), onlineOnly = false, urgent = true)

    assertThat(sentWith).containsExactly(UserBasedAuthorization.AccessKey(accessKey))
  }

  @Test
  fun `zero access key uses UnrestrictedUnauthenticatedAccess auth`() = runTest {
    val api = apiReturning(success)

    api.sendSealedSenderMessage(recipient, 1L, emptyList(), individualUnidentifiedAccessFirst(ByteArray(16)), onlineOnly = false, urgent = true)

    assertThat(sentWith).containsExactly(UserBasedAuthorization.UnrestrictedUnauthenticatedAccess)
  }

  @Test
  fun `group send token uses GroupSend auth`() = runTest {
    val api = apiReturning(success)

    api.sendSealedSenderMessage(recipient, 1L, emptyList(), SealedSenderAccess.IndividualGroupSendTokenFirst(token, mockk()), onlineOnly = false, urgent = true)

    assertThat(sentWith).containsExactly(UserBasedAuthorization.GroupSend(token))
  }

  @Test
  fun `rejected access key is retried once with the group send token`() = runTest {
    val api = apiReturning(unauthorized, success)

    val result = api.sendSealedSenderMessage(recipient, 1L, emptyList(), individualUnidentifiedAccessFirst(accessKey) { token }, onlineOnly = false, urgent = true)

    assertThat(result).isEqualTo(success)
    assertThat(sentWith).containsExactly(UserBasedAuthorization.AccessKey(accessKey), UserBasedAuthorization.GroupSend(token))
  }

  @Test
  fun `rejected access key without a fallback returns the rejection`() = runTest {
    val api = apiReturning(unauthorized)

    val result = api.sendSealedSenderMessage(recipient, 1L, emptyList(), individualUnidentifiedAccessFirst(accessKey), onlineOnly = false, urgent = true)

    assertThat(result).isEqualTo(unauthorized)
    assertThat(sentWith).containsExactly(UserBasedAuthorization.AccessKey(accessKey))
  }

  @Test
  fun `rejected fallback returns the second rejection`() = runTest {
    val api = apiReturning(unauthorized, unauthorized)

    val result = api.sendSealedSenderMessage(recipient, 1L, emptyList(), individualUnidentifiedAccessFirst(accessKey) { token }, onlineOnly = false, urgent = true)

    assertThat(result).isEqualTo(unauthorized)
    assertThat(sentWith).containsExactly(UserBasedAuthorization.AccessKey(accessKey), UserBasedAuthorization.GroupSend(token))
  }

  @Test
  fun `failure other than a rejected access is not retried`() = runTest {
    val mismatched: RequestResult<Unit, SealedSendFailure> = RequestResult.NonSuccess(MismatchedDeviceException("mismatched", emptyArray()))
    val api = apiReturning(mismatched)

    val result = api.sendSealedSenderMessage(recipient, 1L, emptyList(), individualUnidentifiedAccessFirst(accessKey) { token }, onlineOnly = false, urgent = true)

    assertThat(result).isEqualTo(mismatched)
    assertThat(sentWith).containsExactly(UserBasedAuthorization.AccessKey(accessKey))
  }

  private fun apiReturning(vararg results: RequestResult<Unit, SealedSendFailure>): MessageApiV2 {
    val api = spyk(MessageApiV2(mockk(), mockk()))
    val remaining = ArrayDeque(results.toList())
    coEvery { api.sendSealed(any(), any(), any(), any(), any(), any()) } coAnswers {
      sentWith += arg<UserBasedSendAuthorization>(3)
      remaining.removeFirst()
    }
    return api
  }

  private fun individualUnidentifiedAccessFirst(
    key: ByteArray,
    createGroupSendToken: SealedSenderAccess.CreateGroupSendToken? = null
  ): SealedSenderAccess.IndividualUnidentifiedAccessFirst {
    val unidentifiedAccess = mockk<UnidentifiedAccess>()
    every { unidentifiedAccess.unidentifiedAccessKey } returns key
    every { unidentifiedAccess.unidentifiedCertificate } returns mockk()
    return SealedSenderAccess.IndividualUnidentifiedAccessFirst(unidentifiedAccess, createGroupSendToken)
  }
}
