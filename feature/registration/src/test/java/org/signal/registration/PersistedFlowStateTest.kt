/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlinx.serialization.json.Json
import org.junit.Test
import org.signal.core.models.AccountEntropyPool
import org.signal.core.models.MasterKey

class PersistedFlowStateTest {

  private val json = Json { ignoreUnknownKeys = true }

  @Test
  fun `round-trip serialization with simple backstack`() {
    val state = PersistedFlowState(
      backStack = listOf(RegistrationRoute.Welcome, RegistrationRoute.PhoneNumberEntry),
      sessionMetadata = null,
      sessionE164 = null,
      doNotAttemptRecoveryPassword = false
    )

    val encoded = json.encodeToString(PersistedFlowState.serializer(), state)
    val decoded = json.decodeFromString(PersistedFlowState.serializer(), encoded)

    assertThat(decoded).isEqualTo(state)
  }

  @Test
  fun `round-trip serialization with nested Permissions route`() {
    val state = PersistedFlowState(
      backStack = listOf(
        RegistrationRoute.Welcome,
        RegistrationRoute.Permissions(nextRoute = RegistrationRoute.PhoneNumberEntry),
        RegistrationRoute.PhoneNumberEntry
      ),
      sessionMetadata = null,
      sessionE164 = "+15551234567",
      doNotAttemptRecoveryPassword = false
    )

    val encoded = json.encodeToString(PersistedFlowState.serializer(), state)
    val decoded = json.decodeFromString(PersistedFlowState.serializer(), encoded)

    assertThat(decoded).isEqualTo(state)
  }

  @Test
  fun `round-trip serialization with VerificationCodeEntry`() {
    val session = NetworkController.SessionMetadata(
      id = "session-123",
      nextSms = 1000L,
      nextCall = 2000L,
      nextVerificationAttempt = 3000L,
      allowedToRequestCode = true,
      requestedInformation = listOf("pushChallenge"),
      verified = false
    )

    val state = PersistedFlowState(
      backStack = listOf(
        RegistrationRoute.Welcome,
        RegistrationRoute.Permissions(nextRoute = RegistrationRoute.PhoneNumberEntry),
        RegistrationRoute.PhoneNumberEntry,
        RegistrationRoute.VerificationCodeEntry
      ),
      sessionMetadata = session,
      sessionE164 = "+15551234567",
      doNotAttemptRecoveryPassword = false
    )

    val encoded = json.encodeToString(PersistedFlowState.serializer(), state)
    val decoded = json.decodeFromString(PersistedFlowState.serializer(), encoded)

    assertThat(decoded).isEqualTo(state)
  }

  @Test
  fun `round-trip serialization with post-registration routes`() {
    val state = PersistedFlowState(
      backStack = listOf(
        RegistrationRoute.Welcome,
        RegistrationRoute.PinCreate,
        RegistrationRoute.ArchiveRestoreSelection.forManualRestore()
      ),
      sessionMetadata = null,
      sessionE164 = "+15551234567",
      doNotAttemptRecoveryPassword = true
    )

    val encoded = json.encodeToString(PersistedFlowState.serializer(), state)
    val decoded = json.decodeFromString(PersistedFlowState.serializer(), encoded)

    assertThat(decoded).isEqualTo(state)
  }

  @Test
  fun `round-trip serialization with PinEntryForRegistrationLock`() {
    val creds = NetworkController.SvrCredentials(username = "user", password = "pass")
    val state = PersistedFlowState(
      backStack = listOf(
        RegistrationRoute.Welcome,
        RegistrationRoute.PinEntryForRegistrationLock(timeRemaining = 86400000L, svrCredentials = creds)
      ),
      sessionMetadata = null,
      sessionE164 = "+15551234567",
      doNotAttemptRecoveryPassword = false
    )

    val encoded = json.encodeToString(PersistedFlowState.serializer(), state)
    val decoded = json.decodeFromString(PersistedFlowState.serializer(), encoded)

    assertThat(decoded).isEqualTo(state)
  }

  @Test
  fun `round-trip serialization with Captcha route`() {
    val session = NetworkController.SessionMetadata(
      id = "session-456",
      nextSms = null,
      nextCall = null,
      nextVerificationAttempt = null,
      allowedToRequestCode = false,
      requestedInformation = listOf("captcha"),
      verified = false
    )

    val state = PersistedFlowState(
      backStack = listOf(
        RegistrationRoute.Welcome,
        RegistrationRoute.PhoneNumberEntry,
        RegistrationRoute.Captcha(session = session)
      ),
      sessionMetadata = session,
      sessionE164 = "+15551234567",
      doNotAttemptRecoveryPassword = false
    )

    val encoded = json.encodeToString(PersistedFlowState.serializer(), state)
    val decoded = json.decodeFromString(PersistedFlowState.serializer(), encoded)

    assertThat(decoded).isEqualTo(state)
  }

  @Test
  fun `deserialization ignores unknown keys for forward compatibility`() {
    val validJson = """{"backStack":[{"type":"org.signal.registration.RegistrationRoute.Welcome"}],"sessionMetadata":null,"sessionE164":null,"doNotAttemptRecoveryPassword":false,"unknownField":"value"}"""
    val decoded = json.decodeFromString(PersistedFlowState.serializer(), validJson)

    assertThat(decoded.backStack).isEqualTo(listOf(RegistrationRoute.Welcome))
    assertThat(decoded.sessionMetadata).isNull()
  }

  @Test
  fun `toPersistedFlowState captures correct fields`() {
    val session = NetworkController.SessionMetadata(
      id = "session-789",
      nextSms = null,
      nextCall = null,
      nextVerificationAttempt = null,
      allowedToRequestCode = true,
      requestedInformation = emptyList(),
      verified = true
    )

    val flowState = RegistrationFlowState(
      backStack = listOf(RegistrationRoute.Welcome, RegistrationRoute.PinCreate),
      sessionMetadata = session,
      sessionE164 = "+15551234567",
      accountEntropyPool = AccountEntropyPool.generate(),
      temporaryMasterKey = MasterKey(ByteArray(32)),
      doNotAttemptRecoveryPassword = true
    )

    val persisted = flowState.toPersistedFlowState()

    assertThat(persisted.backStack).isEqualTo(flowState.backStack)
    assertThat(persisted.sessionMetadata).isEqualTo(session)
    assertThat(persisted.sessionE164).isEqualTo("+15551234567")
    assertThat(persisted.doNotAttemptRecoveryPassword).isEqualTo(true)
  }

  @Test
  fun `toRegistrationFlowState reconstructs all fields`() {
    val session = NetworkController.SessionMetadata(
      id = "session-101",
      nextSms = null,
      nextCall = null,
      nextVerificationAttempt = null,
      allowedToRequestCode = true,
      requestedInformation = emptyList(),
      verified = true
    )

    val persisted = PersistedFlowState(
      backStack = listOf(RegistrationRoute.Welcome, RegistrationRoute.PinCreate),
      sessionMetadata = session,
      sessionE164 = "+15551234567",
      doNotAttemptRecoveryPassword = true
    )

    val aep = AccountEntropyPool.generate()
    val masterKey = MasterKey(ByteArray(32))

    val flowState = persisted.toRegistrationFlowState(
      accountEntropyPool = aep,
      temporaryMasterKey = masterKey,
      preExistingRegistrationData = null
    )

    assertThat(flowState.backStack).isEqualTo(persisted.backStack)
    assertThat(flowState.sessionMetadata).isEqualTo(session)
    assertThat(flowState.sessionE164).isEqualTo("+15551234567")
    assertThat(flowState.accountEntropyPool).isEqualTo(aep)
    assertThat(flowState.temporaryMasterKey).isEqualTo(masterKey)
    assertThat(flowState.preExistingRegistrationData).isNull()
    assertThat(flowState.doNotAttemptRecoveryPassword).isEqualTo(true)
  }
}
