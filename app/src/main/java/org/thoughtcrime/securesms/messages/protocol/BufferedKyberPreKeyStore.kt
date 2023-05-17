/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.messages.protocol

import org.signal.libsignal.protocol.InvalidKeyIdException
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.KyberPreKeyStore
import org.thoughtcrime.securesms.database.KyberPreKeyTable.KyberPreKey
import org.thoughtcrime.securesms.database.SignalDatabase
import org.whispersystems.signalservice.api.SignalServiceAccountDataStore
import org.whispersystems.signalservice.api.push.ServiceId

/**
 * An in-memory kyber prekey store that is intended to be used temporarily while decrypting messages.
 */
class BufferedKyberPreKeyStore(private val selfServiceId: ServiceId) : KyberPreKeyStore {

  /** Our in-memory cache of kyber prekeys. */
  val store: MutableMap<Int, KyberPreKey> = mutableMapOf()

  /** Whether or not we've done a loadAll operation. Let's us avoid doing it twice. */
  private var hasLoadedAll: Boolean = false

  /** The kyber prekeys that have been marked as removed (if they're not last resort). */
  private val removedIfNotLastResort: MutableList<Int> = mutableListOf()

  @kotlin.jvm.Throws(InvalidKeyIdException::class)
  override fun loadKyberPreKey(kyberPreKeyId: Int): KyberPreKeyRecord {
    return store.computeIfAbsent(kyberPreKeyId) {
      SignalDatabase.kyberPreKeys.get(selfServiceId, kyberPreKeyId) ?: throw InvalidKeyIdException("Missing kyber prekey with ID: $kyberPreKeyId")
    }.record
  }

  override fun loadKyberPreKeys(): List<KyberPreKeyRecord> {
    return if (hasLoadedAll) {
      store.values.map { it.record }
    } else {
      val models = SignalDatabase.kyberPreKeys.getAll(selfServiceId)
      models.forEach { store[it.record.id] = it }
      hasLoadedAll = true

      models.map { it.record }
    }
  }

  override fun storeKyberPreKey(kyberPreKeyId: Int, record: KyberPreKeyRecord) {
    error("This method is only used in tests")
  }

  override fun containsKyberPreKey(kyberPreKeyId: Int): Boolean {
    loadKyberPreKey(kyberPreKeyId)
    return store.containsKey(kyberPreKeyId)
  }

  override fun markKyberPreKeyUsed(kyberPreKeyId: Int) {
    loadKyberPreKey(kyberPreKeyId)

    store[kyberPreKeyId]?.let {
      if (!it.lastResort) {
        store.remove(kyberPreKeyId)
      }
    }

    removedIfNotLastResort += kyberPreKeyId
  }

  fun flushToDisk(persistentStore: SignalServiceAccountDataStore) {
    for (id in removedIfNotLastResort) {
      persistentStore.markKyberPreKeyUsed(id)
    }
  }
}
