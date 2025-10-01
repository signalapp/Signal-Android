/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.util

import org.junit.Assert.assertEquals
import org.signal.core.util.readToSingleObject
import org.signal.core.util.requireLongOrNull
import org.signal.core.util.select
import org.signal.core.util.update
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.kem.KEMKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyType
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.thoughtcrime.securesms.database.KyberPreKeyTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import org.whispersystems.signalservice.api.push.ServiceId.PNI
import java.security.SecureRandom

object KyberPreKeysTestUtil {
  fun insertTestRecord(account: ServiceId, id: Int, staleTime: Long = 0, lastResort: Boolean = false) {
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

    val count = SignalDatabase.rawDatabase
      .update(KyberPreKeyTable.TABLE_NAME)
      .values(KyberPreKeyTable.STALE_TIMESTAMP to staleTime)
      .where("${KyberPreKeyTable.ACCOUNT_ID} = ? AND ${KyberPreKeyTable.KEY_ID} = $id", account.toAccountId())
      .run()

    assertEquals(1, count)
  }

  fun getStaleTime(account: ServiceId, id: Int): Long? {
    return SignalDatabase.rawDatabase
      .select(KyberPreKeyTable.STALE_TIMESTAMP)
      .from(KyberPreKeyTable.TABLE_NAME)
      .where("${KyberPreKeyTable.ACCOUNT_ID} = ? AND ${KyberPreKeyTable.KEY_ID} = $id", account.toAccountId())
      .run()
      .readToSingleObject { it.requireLongOrNull(KyberPreKeyTable.STALE_TIMESTAMP) }
  }

  fun generateECPublicKey(): ECPublicKey {
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
