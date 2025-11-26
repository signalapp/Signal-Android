/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.messages.protocol

import org.signal.libsignal.protocol.InvalidKeyIdException
import org.signal.libsignal.protocol.ReusedBaseKeyException
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.thoughtcrime.securesms.database.KyberPreKeyTable.KyberPreKey
import org.thoughtcrime.securesms.database.SignalDatabase
import org.whispersystems.signalservice.api.SignalServiceAccountDataStore
import org.whispersystems.signalservice.api.SignalServiceKyberPreKeyStore
import org.whispersystems.signalservice.api.push.ServiceId

/**
 * An in-memory kyber prekey store that is intended to be used temporarily while decrypting messages.
 */
class BufferedKyberPreKeyStore(private val selfServiceId: ServiceId) : SignalServiceKyberPreKeyStore {

  /** Our in-memory cache of kyber prekeys. */
  val store: MutableMap<Int, KyberPreKey> = mutableMapOf()

  /** Whether or not we've done a loadAll operation. Let's us avoid doing it twice. */
  private var hasLoadedAll: Boolean = false

  /** The kyber prekeys that have been marked as removed (if they're not last resort). */
  private val removedIfNotLastResort: MutableSet<Triple<Int, Int, ECPublicKey>> = mutableSetOf()

  /** Tuples of last-resort key data we've already seen. */
  private val lastResortKeyTuples: MutableSet<Triple<Int, Int, ECPublicKey>> = mutableSetOf()

  /** A separate list of tuples to flush so we don't try to flush the same one multiple times */
  private val unFlushedLastResortKeyTuples: MutableSet<Triple<Int, Int, ECPublicKey>> = mutableSetOf()

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

  override fun loadLastResortKyberPreKeys(): List<KyberPreKeyRecord> {
    error("Not expected in this flow")
  }

  override fun storeKyberPreKey(kyberPreKeyId: Int, record: KyberPreKeyRecord) {
    error("Not expected in this flow")
  }

  override fun storeLastResortKyberPreKey(kyberPreKeyId: Int, kyberPreKeyRecord: KyberPreKeyRecord) {
    error("Not expected in this flow")
  }

  override fun containsKyberPreKey(kyberPreKeyId: Int): Boolean {
    loadKyberPreKey(kyberPreKeyId)
    return store.containsKey(kyberPreKeyId)
  }

  override fun markKyberPreKeyUsed(kyberPreKeyId: Int, signedPreKeyId: Int, publicKey: ECPublicKey) {
    loadKyberPreKey(kyberPreKeyId)

    store[kyberPreKeyId]?.let {
      if (!it.lastResort) {
        store.remove(kyberPreKeyId)
        removedIfNotLastResort += Triple(kyberPreKeyId, signedPreKeyId, publicKey)
      } else {
        if (!lastResortKeyTuples.add(Triple(kyberPreKeyId, signedPreKeyId, publicKey))) {
          throw ReusedBaseKeyException()
        }

        unFlushedLastResortKeyTuples += Triple(kyberPreKeyId, signedPreKeyId, publicKey)
      }
    }
  }

  override fun removeKyberPreKey(kyberPreKeyId: Int) {
    error("Not expected in this flow")
  }

  override fun markAllOneTimeKyberPreKeysStaleIfNecessary(staleTime: Long) {
    error("Not expected in this flow")
  }

  override fun deleteAllStaleOneTimeKyberPreKeys(threshold: Long, minCount: Int) {
    error("Not expected in this flow")
  }

  fun flushToDisk(persistentStore: SignalServiceAccountDataStore) {
    val tuples = removedIfNotLastResort + unFlushedLastResortKeyTuples
    unFlushedLastResortKeyTuples.clear()

    for ((key, signedKey, publicKey) in tuples) {
      persistentStore.markKyberPreKeyUsed(key, signedKey, publicKey)
    }

    unFlushedLastResortKeyTuples.clear()
  }
}
