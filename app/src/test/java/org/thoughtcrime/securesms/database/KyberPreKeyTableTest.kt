/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.ServiceId
import org.signal.core.models.ServiceId.ACI
import org.signal.core.models.ServiceId.PNI
import org.signal.core.util.readToSingleObject
import org.signal.core.util.requireLongOrNull
import org.signal.core.util.select
import org.signal.core.util.update
import org.signal.libsignal.protocol.ReusedBaseKeyException
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.kem.KEMKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyType
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import org.thoughtcrime.securesms.testutil.SignalDatabaseRule
import java.security.SecureRandom
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class KyberPreKeyTableTest {

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  @get:Rule
  val signalDatabaseRule = SignalDatabaseRule()

  private val aci: ACI = ACI.from(UUID.randomUUID())
  private val pni: PNI = PNI.from(UUID.randomUUID())

  @Test
  fun markAllStaleIfNecessary_onlyUpdatesMatchingAccountAndZeroValues() {
    insertTestRecord(aci, id = 1)
    insertTestRecord(aci, id = 2)
    insertTestRecord(aci, id = 3, staleTime = 42)
    insertTestRecord(pni, id = 4)

    val now = System.currentTimeMillis()
    SignalDatabase.kyberPreKeys.markAllStaleIfNecessary(aci, now)

    assertEquals(now, getStaleTime(aci, 1))
    assertEquals(now, getStaleTime(aci, 2))
    assertEquals(42L, getStaleTime(aci, 3))
    assertEquals(0L, getStaleTime(pni, 4))
  }

  @Test
  fun deleteAllStaleBefore_deleteOldBeforeThreshold() {
    insertTestRecord(aci, id = 1, staleTime = 10)
    insertTestRecord(aci, id = 2, staleTime = 10)
    insertTestRecord(aci, id = 3, staleTime = 10)
    insertTestRecord(aci, id = 4, staleTime = 15)
    insertTestRecord(aci, id = 5, staleTime = 0)

    SignalDatabase.kyberPreKeys.deleteAllStaleBefore(aci, threshold = 11, minCount = 0)

    assertNull(getStaleTime(aci, 1))
    assertNull(getStaleTime(aci, 2))
    assertNull(getStaleTime(aci, 3))
    assertNotNull(getStaleTime(aci, 4))
    assertNotNull(getStaleTime(aci, 5))
  }

  @Test
  fun deleteAllStaleBefore_neverDeleteStaleOfZero() {
    insertTestRecord(aci, id = 1, staleTime = 0)
    insertTestRecord(aci, id = 2, staleTime = 0)
    insertTestRecord(aci, id = 3, staleTime = 0)
    insertTestRecord(aci, id = 4, staleTime = 0)
    insertTestRecord(aci, id = 5, staleTime = 0)

    SignalDatabase.kyberPreKeys.deleteAllStaleBefore(aci, threshold = 10, minCount = 1)

    assertNotNull(getStaleTime(aci, 1))
    assertNotNull(getStaleTime(aci, 2))
    assertNotNull(getStaleTime(aci, 3))
    assertNotNull(getStaleTime(aci, 4))
    assertNotNull(getStaleTime(aci, 5))
  }

  @Test
  fun deleteAllStaleBefore_respectMinCount() {
    insertTestRecord(aci, id = 1, staleTime = 10)
    insertTestRecord(aci, id = 2, staleTime = 10)
    insertTestRecord(aci, id = 3, staleTime = 10)
    insertTestRecord(aci, id = 4, staleTime = 10)
    insertTestRecord(aci, id = 5, staleTime = 10)

    SignalDatabase.kyberPreKeys.deleteAllStaleBefore(aci, threshold = 11, minCount = 3)

    assertNull(getStaleTime(aci, 1))
    assertNull(getStaleTime(aci, 2))
    assertNotNull(getStaleTime(aci, 3))
    assertNotNull(getStaleTime(aci, 4))
    assertNotNull(getStaleTime(aci, 5))
  }

  @Test
  fun deleteAllStaleBefore_respectAccount() {
    insertTestRecord(aci, id = 1, staleTime = 10)
    insertTestRecord(aci, id = 2, staleTime = 10)
    insertTestRecord(aci, id = 3, staleTime = 10)

    insertTestRecord(pni, id = 4, staleTime = 10)
    insertTestRecord(pni, id = 5, staleTime = 10)

    SignalDatabase.kyberPreKeys.deleteAllStaleBefore(aci, threshold = 11, minCount = 2)

    assertNull(getStaleTime(aci, 1))
    assertNotNull(getStaleTime(aci, 2))
    assertNotNull(getStaleTime(aci, 3))
    assertNotNull(getStaleTime(pni, 4))
    assertNotNull(getStaleTime(pni, 5))
  }

  @Test
  fun deleteAllStaleBefore_ignoreLastResortForMinCount() {
    insertTestRecord(aci, id = 1, staleTime = 10)
    insertTestRecord(aci, id = 2, staleTime = 10)
    insertTestRecord(aci, id = 3, staleTime = 10)
    insertTestRecord(aci, id = 4, staleTime = 10)
    insertTestRecord(aci, id = 5, staleTime = 10, lastResort = true)

    SignalDatabase.kyberPreKeys.deleteAllStaleBefore(aci, threshold = 11, minCount = 3)

    assertNull(getStaleTime(aci, 1))
    assertNotNull(getStaleTime(aci, 2))
    assertNotNull(getStaleTime(aci, 3))
    assertNotNull(getStaleTime(aci, 4))
    assertNotNull(getStaleTime(aci, 5))
  }

  @Test
  fun deleteAllStaleBefore_neverDeleteLastResort() {
    insertTestRecord(aci, id = 1, staleTime = 10, lastResort = true)
    insertTestRecord(aci, id = 2, staleTime = 10, lastResort = true)
    insertTestRecord(aci, id = 3, staleTime = 10, lastResort = true)

    SignalDatabase.kyberPreKeys.deleteAllStaleBefore(aci, threshold = 11, minCount = 0)

    assertNotNull(getStaleTime(aci, 1))
    assertNotNull(getStaleTime(aci, 2))
    assertNotNull(getStaleTime(aci, 3))
  }

  @Test(expected = ReusedBaseKeyException::class)
  fun handleMarkKyberPreKeyUsed_doesNotAllowDuplicateLastResortKeyEntries() {
    insertTestRecord(aci, id = 1, staleTime = 10, lastResort = true)
    val publicKey = generateECPublicKey()

    SignalDatabase.kyberPreKeys.handleMarkKyberPreKeyUsed(
      serviceId = aci,
      kyberPreKeyId = 1,
      signedPreKeyId = 1,
      baseKey = publicKey
    )

    SignalDatabase.kyberPreKeys.handleMarkKyberPreKeyUsed(
      serviceId = aci,
      kyberPreKeyId = 1,
      signedPreKeyId = 1,
      baseKey = publicKey
    )
  }

  @Test
  fun handleMarkKyberPreKeyUsed_allowDuplicateNonLastResortKeyEntries() {
    insertTestRecord(aci, id = 1, staleTime = 10, lastResort = false)
    val publicKey = generateECPublicKey()

    SignalDatabase.kyberPreKeys.handleMarkKyberPreKeyUsed(
      serviceId = aci,
      kyberPreKeyId = 1,
      signedPreKeyId = 1,
      baseKey = publicKey
    )

    SignalDatabase.kyberPreKeys.handleMarkKyberPreKeyUsed(
      serviceId = aci,
      kyberPreKeyId = 1,
      signedPreKeyId = 1,
      baseKey = publicKey
    )
  }

  private fun insertTestRecord(account: ServiceId, id: Int, staleTime: Long = 0, lastResort: Boolean = false) {
    val kemKeyPair = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
    SignalDatabase.kyberPreKeys.insert(
      serviceId = account,
      keyId = id,
      record = KyberPreKeyRecord(
        id,
        System.currentTimeMillis(),
        kemKeyPair,
        ECKeyPair.generate().privateKey.calculateSignature(kemKeyPair.publicKey.serialize())
      ),
      lastResort = lastResort
    )

    val count = SignalDatabase.writableDatabase
      .update(KyberPreKeyTable.TABLE_NAME)
      .values(KyberPreKeyTable.STALE_TIMESTAMP to staleTime)
      .where("${KyberPreKeyTable.ACCOUNT_ID} = ? AND ${KyberPreKeyTable.KEY_ID} = $id", account.toAccountId())
      .run()

    assertEquals(1, count)
  }

  private fun getStaleTime(account: ServiceId, id: Int): Long? {
    return SignalDatabase.writableDatabase
      .select(KyberPreKeyTable.STALE_TIMESTAMP)
      .from(KyberPreKeyTable.TABLE_NAME)
      .where("${KyberPreKeyTable.ACCOUNT_ID} = ? AND ${KyberPreKeyTable.KEY_ID} = $id", account.toAccountId())
      .run()
      .readToSingleObject { it.requireLongOrNull(KyberPreKeyTable.STALE_TIMESTAMP) }
  }

  private fun generateECPublicKey(): ECPublicKey {
    val byteArray = ByteArray(ECPublicKey.KEY_SIZE - 1)
    SecureRandom().nextBytes(byteArray)

    return ECPublicKey.fromPublicKeyBytes(byteArray)
  }

  private fun ServiceId.toAccountId(): String {
    return when (this) {
      is ACI -> this.toString()
      is PNI -> KyberPreKeyTable.PNI_ACCOUNT_ID
    }
  }
}
