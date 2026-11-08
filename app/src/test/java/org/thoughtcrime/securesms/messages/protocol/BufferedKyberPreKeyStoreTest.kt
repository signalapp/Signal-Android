/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.messages.protocol

import android.app.Application
import io.mockk.mockk
import io.mockk.verify
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.ServiceId.ACI
import org.signal.libsignal.protocol.ReusedBaseKeyException
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.kem.KEMKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyType
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import org.thoughtcrime.securesms.testutil.SignalDatabaseRule
import org.whispersystems.signalservice.api.SignalServiceAccountDataStore
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class BufferedKyberPreKeyStoreTest {

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  @get:Rule
  val signalDatabaseRule = SignalDatabaseRule()

  private val aci: ACI = ACI.from(UUID.randomUUID())
  private val testSubject = BufferedKyberPreKeyStore(aci)
  private val dataStore: SignalServiceAccountDataStore = mockk(relaxed = true)

  @Test
  fun givenALastResortKey_whenIMarkKyberPreKeyUsed_thenIExpectNoIssues() {
    insertLastResortKey(id = 1)
    val publicKey = generateECPublicKey()

    testSubject.markKyberPreKeyUsed(
      kyberPreKeyId = 1,
      signedPreKeyId = 2,
      publicKey = publicKey
    )
  }

  @Test(expected = ReusedBaseKeyException::class)
  fun givenALastResortKey_whenIMarkKyberPreKeyUsedTwice_thenIExpectException() {
    insertLastResortKey(id = 1)
    val publicKey = generateECPublicKey()

    testSubject.markKyberPreKeyUsed(
      kyberPreKeyId = 1,
      signedPreKeyId = 2,
      publicKey = publicKey
    )

    testSubject.markKyberPreKeyUsed(
      kyberPreKeyId = 1,
      signedPreKeyId = 2,
      publicKey = publicKey
    )
  }

  @Test(expected = ReusedBaseKeyException::class)
  fun givenALastResortKeyUsedInAnEarlierBatch_whenIMarkKyberPreKeyUsed_thenIExpectException() {
    insertLastResortKey(id = 1)
    val publicKey = generateECPublicKey()

    SignalDatabase.kyberPreKeys.handleMarkKyberPreKeyUsed(
      serviceId = aci,
      kyberPreKeyId = 1,
      signedPreKeyId = 2,
      baseKey = publicKey
    )

    testSubject.markKyberPreKeyUsed(
      kyberPreKeyId = 1,
      signedPreKeyId = 2,
      publicKey = publicKey
    )
  }

  @Test
  fun givenAMarkedLastResortKey_whenIFlushTwice_thenIExpectOnlyOneWrite() {
    insertLastResortKey(id = 1)
    val publicKey = generateECPublicKey()

    testSubject.markKyberPreKeyUsed(
      kyberPreKeyId = 1,
      signedPreKeyId = 2,
      publicKey = publicKey
    )

    testSubject.flushToDisk(dataStore)
    testSubject.flushToDisk(dataStore)

    verify(exactly = 1) { dataStore.markKyberPreKeyUsed(1, 2, publicKey) }
  }

  private fun insertLastResortKey(id: Int) {
    val kemKeyPair = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
    SignalDatabase.kyberPreKeys.insert(
      serviceId = aci,
      keyId = id,
      record = KyberPreKeyRecord(
        id,
        System.currentTimeMillis(),
        kemKeyPair,
        ECKeyPair.generate().privateKey.calculateSignature(kemKeyPair.publicKey.serialize())
      ),
      lastResort = true
    )
  }

  private fun generateECPublicKey(): ECPublicKey {
    return ECKeyPair.generate().publicKey
  }
}
