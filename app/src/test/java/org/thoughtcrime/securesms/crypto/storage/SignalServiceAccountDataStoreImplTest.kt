/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.crypto.storage

import android.content.Context
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.state.IdentityKeyStore
import org.signal.libsignal.protocol.state.SessionRecord
import org.whispersystems.signalservice.test.LibSignalLibraryUtil.assumeLibSignalSupportedOnOS
import java.util.UUID

class SignalServiceAccountDataStoreImplTest {

  private val identityKeyStore = mockk<SignalIdentityKeyStore>(relaxed = true)
  private val sessionStore = mockk<TextSecureSessionStore>(relaxed = true)
  private val senderKeyStore = mockk<SignalSenderKeyStore>(relaxed = true)

  private val subject = SignalServiceAccountDataStoreImpl(
    mockk<Context>(),
    mockk<TextSecurePreKeyStore>(),
    mockk<SignalKyberPreKeyStore>(),
    identityKeyStore,
    sessionStore,
    senderKeyStore
  )

  private val canonical = SignalProtocolAddress(UUID.randomUUID().toString(), 2)
  private val uppercase = SignalProtocolAddress(canonical.name.uppercase(), 2)

  @Before
  fun ensureNativeSupported() {
    assumeLibSignalSupportedOnOS()
  }

  @Test
  fun `identity operations use the canonical address`() {
    val identityKey = IdentityKey(ECKeyPair.generate().publicKey)

    subject.getIdentity(uppercase)
    subject.saveIdentity(uppercase, identityKey)
    subject.isTrustedIdentity(uppercase, identityKey, IdentityKeyStore.Direction.RECEIVING)

    verify { identityKeyStore.getIdentity(canonical) }
    verify { identityKeyStore.saveIdentity(canonical, identityKey) }
    verify { identityKeyStore.isTrustedIdentity(canonical, identityKey, IdentityKeyStore.Direction.RECEIVING) }
  }

  @Test
  fun `session operations use the canonical address`() {
    val record = SessionRecord()

    subject.loadSession(uppercase)
    subject.storeSession(uppercase, record)
    subject.containsSession(uppercase)
    subject.loadExistingSessions(listOf(uppercase))
    subject.getSubDeviceSessions(uppercase.name)

    verify { sessionStore.loadSession(canonical) }
    verify { sessionStore.storeSession(canonical, record) }
    verify { sessionStore.containsSession(canonical) }
    verify { sessionStore.loadExistingSessions(listOf(canonical)) }
    verify { sessionStore.getSubDeviceSessions(canonical.name) }
  }

  @Test
  fun `sender key operations use the canonical address`() {
    val distributionId = UUID.randomUUID()

    subject.loadSenderKey(uppercase, distributionId)
    subject.clearSenderKeySharedWith(listOf(uppercase))

    verify { senderKeyStore.loadSenderKey(canonical, distributionId) }
    verify { senderKeyStore.clearSenderKeySharedWith(listOf(canonical)) }
  }
}
