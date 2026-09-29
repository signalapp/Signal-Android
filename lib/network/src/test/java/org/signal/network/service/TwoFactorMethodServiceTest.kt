/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.service

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.signal.core.models.MasterKey
import org.signal.libsignal.net.ConfirmedMfaKey
import org.signal.libsignal.net.MfaKeyKind
import org.signal.libsignal.net.MfaKeyNotFoundException
import org.signal.libsignal.net.MfaMetadata
import org.signal.libsignal.net.RequestResult
import org.signal.network.api.AccountApiV2
import java.io.IOException
import java.time.Instant

class TwoFactorMethodServiceTest {

  companion object {
    private val CREATED_AT = Instant.ofEpochMilli(1_700_000_000_000L)
    private const val KEY_ID = 1

    private val MASTER_KEY = MasterKey(ByteArray(32) { (it + 100).toByte() })

    private fun key(id: Int, name: String?, kind: MfaKeyKind) = ConfirmedMfaKey(
      id = id,
      metadata = name?.let { MfaMetadata(name = it, createdAt = CREATED_AT) },
      kind = kind
    )
  }

  private val accountApi = mockk<AccountApiV2>()
  private val service = TwoFactorMethodService(accountApi)

  @Before
  fun setUp() {
    coEvery { accountApi.listMfaKeys(any()) } returns RequestResult.Success(emptyList())
    coEvery { accountApi.setMfaKeyMetadata(any(), any(), any()) } returns RequestResult.Success(Unit)
    coEvery { accountApi.removeMfaKey(any()) } returns RequestResult.Success(Unit)
  }

  @Test
  fun `a confirmed key comes back with its id, kind, and metadata`() = runTest {
    coEvery { accountApi.listMfaKeys(any()) } returns RequestResult.Success(listOf(key(KEY_ID, "Aegis", MfaKeyKind.TOTP)))

    val methods = (service.getMethods(MASTER_KEY) as RequestResult.Success).result

    assertThat(methods).containsExactly(
      TwoFactorMethodService.TwoFactorMethod(id = KEY_ID.toLong(), kind = MfaKeyKind.TOTP, name = "Aegis", createdAt = CREATED_AT)
    )
  }

  /** The caller still needs to be able to see and remove a method even if its name can't be decrypted. */
  @Test
  fun `a key whose metadata can't be read still comes back, without a name or date`() = runTest {
    coEvery { accountApi.listMfaKeys(any()) } returns RequestResult.Success(listOf(key(KEY_ID, null, MfaKeyKind.TOTP)))

    val methods = (service.getMethods(MASTER_KEY) as RequestResult.Success).result

    assertThat(methods).containsExactly(
      TwoFactorMethodService.TwoFactorMethod(id = KEY_ID.toLong(), kind = MfaKeyKind.TOTP, name = null, createdAt = null)
    )
  }

  /** The list is about what's on the account, not what this client understands, so a newer device's key still comes back. */
  @Test
  fun `a key of a kind this client doesn't know still comes back`() = runTest {
    coEvery { accountApi.listMfaKeys(any()) } returns RequestResult.Success(listOf(key(KEY_ID, "Future", MfaKeyKind.UNKNOWN)))

    val methods = (service.getMethods(MASTER_KEY) as RequestResult.Success).result

    assertThat(methods).containsExactly(
      TwoFactorMethodService.TwoFactorMethod(id = KEY_ID.toLong(), kind = MfaKeyKind.UNKNOWN, name = "Future", createdAt = CREATED_AT)
    )
  }

  /** Ids are only unique within a kind, so a passkey and an app that share one have to stay separate entries. */
  @Test
  fun `an app and a passkey sharing an id both come back`() = runTest {
    coEvery { accountApi.listMfaKeys(any()) } returns RequestResult.Success(
      listOf(key(KEY_ID, "Pixel Phone", MfaKeyKind.WEB_AUTHN), key(KEY_ID, "Aegis", MfaKeyKind.TOTP))
    )

    val methods = (service.getMethods(MASTER_KEY) as RequestResult.Success).result

    assertThat(methods.map { it.kind }).containsExactly(MfaKeyKind.WEB_AUTHN, MfaKeyKind.TOTP)
  }

  @Test
  fun `a list we couldn't fetch stays a network error`() = runTest {
    coEvery { accountApi.listMfaKeys(any()) } returns RequestResult.RetryableNetworkError(IOException("offline"))

    assertThat(service.getMethods(MASTER_KEY)).isInstanceOf(RequestResult.RetryableNetworkError::class)
  }

  @Test
  fun `setting a name hands back the date it was given`() = runTest {
    val metadata = slot<MfaMetadata>()
    coEvery { accountApi.setMfaKeyMetadata(eq(KEY_ID), capture(metadata), any()) } returns RequestResult.Success(Unit)

    assertThat(service.setName(KEY_ID.toLong(), "Aegis", CREATED_AT, MASTER_KEY)).isEqualTo(RequestResult.Success(Unit))

    assertThat(metadata.captured.name).isEqualTo("Aegis")
    assertThat(metadata.captured.createdAt).isEqualTo(CREATED_AT)
  }

  @Test
  fun `a name for a key the service no longer has stays a not-found error`() = runTest {
    val error = MfaKeyNotFoundException("gone")
    coEvery { accountApi.setMfaKeyMetadata(any(), any(), any()) } returns RequestResult.NonSuccess(error)

    assertThat(service.setName(KEY_ID.toLong(), "Aegis", CREATED_AT, MASTER_KEY)).isEqualTo(RequestResult.NonSuccess(error))
  }

  @Test
  fun `removing a method removes its key`() = runTest {
    assertThat(service.removeMethod(KEY_ID.toLong())).isEqualTo(RequestResult.Success(Unit))

    coVerify { accountApi.removeMfaKey(KEY_ID) }
  }

  @Test
  fun `a removal we couldn't send stays a network error`() = runTest {
    coEvery { accountApi.removeMfaKey(any()) } returns RequestResult.RetryableNetworkError(IOException("offline"))

    assertThat(service.removeMethod(KEY_ID.toLong())).isInstanceOf(RequestResult.RetryableNetworkError::class)
  }
}
