/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.signal.core.models.MasterKey
import org.signal.libsignal.net.MfaMetadata
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.net.TooManyMfaKeysException
import org.signal.libsignal.net.WebAuthnCreateParameters
import org.signal.libsignal.net.WebAuthnRegistrationUnsuccessfulException
import org.signal.network.api.AccountApiV2
import org.thoughtcrime.securesms.components.settings.app.account.AccountSettingsRepository.FinishPasskeyRegistrationResult
import org.thoughtcrime.securesms.components.settings.app.account.AccountSettingsRepository.StartPasskeyRegistrationResult
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset

class AccountSettingsRepositoryTest {

  companion object {
    /** 2023-11-14T22:13:20Z, which is the date the credential gets labelled with. */
    private const val NOW = 1_700_000_000_000L
    private const val PASSKEY_ID = 4
    private const val RELYING_PARTY_ID = "login.signal.org"
    private const val DEFAULT_NAME = "Passkey"

    private val MASTER_KEY = MasterKey(ByteArray(32) { (it + 100).toByte() })

    private val USER_HANDLE = byteArrayOf(1, 2, 3)
    private val EXCLUDE_CREDENTIAL_ID = byteArrayOf(7, 8, 9)
    private val CREATE_PARAMETERS = WebAuthnCreateParameters(USER_HANDLE, listOf(-7, -257), listOf(EXCLUDE_CREDENTIAL_ID))

    private val ATTESTATION_OBJECT = byteArrayOf(4, 5, 6)
    private const val CLIENT_DATA_JSON = "{\"type\":\"webauthn.create\"}"
  }

  private val api = mockk<AccountApiV2>()
  private val repository = AccountSettingsRepository(
    defaultPasskeyName = DEFAULT_NAME,
    api = api,
    masterKeyProvider = { MASTER_KEY },
    clock = { NOW },
    zoneId = { ZoneOffset.UTC },
    relyingPartyId = RELYING_PARTY_ID
  )

  @Before
  fun setUp() {
    coEvery { api.startWebAuthnRegistration() } returns RequestResult.Success(CREATE_PARAMETERS)
    coEvery { api.finishWebAuthnRegistration(any(), any(), any(), any()) } returns RequestResult.Success(PASSKEY_ID)
  }

  @Test
  fun `starting a ceremony passes the service's parameters on to the authenticator`() = runTest {
    val result = repository.startPasskeyRegistration()

    assertThat(result).isInstanceOf(StartPasskeyRegistrationResult.Success::class)
    val parameters = (result as StartPasskeyRegistrationResult.Success).parameters

    assertThat(parameters.relyingPartyId).isEqualTo(RELYING_PARTY_ID)
    assertThat(parameters.relyingPartyName).isEqualTo("Signal")
    assertThat(parameters.userHandle).isEqualTo(USER_HANDLE)
    assertThat(parameters.allowedAlgorithms).isEqualTo(listOf(-7, -257))
    assertThat(parameters.excludeCredentialIds).isEqualTo(listOf(EXCLUDE_CREDENTIAL_ID))
  }

  /** The label is what the provider shows the user later, and is a date rather than anything identifying. */
  @Test
  fun `starting a ceremony labels the credential with the current date`() = runTest {
    val result = repository.startPasskeyRegistration() as StartPasskeyRegistrationResult.Success

    assertThat(result.parameters.userName).isEqualTo("2023-11-14")
  }

  @Test
  fun `an account already at its limit is reported rather than starting a ceremony`() = runTest {
    coEvery { api.startWebAuthnRegistration() } returns RequestResult.NonSuccess(TooManyMfaKeysException("full"))

    assertThat(repository.startPasskeyRegistration()).isEqualTo(StartPasskeyRegistrationResult.TooManyMethods)
  }

  @Test
  fun `a start we couldn't send is a network failure`() = runTest {
    coEvery { api.startWebAuthnRegistration() } returns RequestResult.RetryableNetworkError(IOException("offline"))

    assertThat(repository.startPasskeyRegistration()).isEqualTo(StartPasskeyRegistrationResult.NetworkFailure)
  }

  @Test
  fun `finishing a ceremony returns the id the service assigned and when it was added`() = runTest {
    val result = repository.finishPasskeyRegistration(ATTESTATION_OBJECT, CLIENT_DATA_JSON)

    assertThat(result).isEqualTo(FinishPasskeyRegistrationResult.Success(passkeyId = PASSKEY_ID.toLong(), createdAt = NOW))
  }

  /** The service wants metadata at registration time, and the user hasn't been asked for a name yet. */
  @Test
  fun `a passkey is registered with a default name, stamped with the time it was added`() = runTest {
    val metadata = slot<MfaMetadata>()
    coEvery { api.finishWebAuthnRegistration(any(), any(), capture(metadata), any()) } returns RequestResult.Success(PASSKEY_ID)

    repository.finishPasskeyRegistration(ATTESTATION_OBJECT, CLIENT_DATA_JSON)

    assertThat(metadata.captured.name).isEqualTo(DEFAULT_NAME)
    assertThat(metadata.captured.createdAt).isEqualTo(Instant.ofEpochMilli(NOW))
  }

  /** Another device can fill the account up while the provider's sheet is open. */
  @Test
  fun `an account that filled up during the ceremony is reported as at its limit`() = runTest {
    coEvery { api.finishWebAuthnRegistration(any(), any(), any(), any()) } returns RequestResult.NonSuccess(TooManyMfaKeysException("full"))

    assertThat(repository.finishPasskeyRegistration(ATTESTATION_OBJECT, CLIENT_DATA_JSON)).isEqualTo(FinishPasskeyRegistrationResult.TooManyMethods)
  }

  @Test
  fun `a ceremony the service won't verify is reported as rejected`() = runTest {
    coEvery { api.finishWebAuthnRegistration(any(), any(), any(), any()) } returns
      RequestResult.NonSuccess(WebAuthnRegistrationUnsuccessfulException("no"))

    assertThat(repository.finishPasskeyRegistration(ATTESTATION_OBJECT, CLIENT_DATA_JSON)).isEqualTo(FinishPasskeyRegistrationResult.CeremonyRejected)
  }

  @Test
  fun `a finish we couldn't send is a network failure`() = runTest {
    coEvery { api.finishWebAuthnRegistration(any(), any(), any(), any()) } returns RequestResult.RetryableNetworkError(IOException("offline"))

    assertThat(repository.finishPasskeyRegistration(ATTESTATION_OBJECT, CLIENT_DATA_JSON)).isEqualTo(FinishPasskeyRegistrationResult.NetworkFailure)
  }
}
