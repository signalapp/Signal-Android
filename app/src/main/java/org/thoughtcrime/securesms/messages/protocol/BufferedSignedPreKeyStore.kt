package org.thoughtcrime.securesms.messages.protocol

import org.signal.libsignal.protocol.InvalidKeyIdException
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyStore
import org.thoughtcrime.securesms.database.SignalDatabase
import org.whispersystems.signalservice.api.SignalServiceAccountDataStore
import org.whispersystems.signalservice.api.push.ServiceId

/**
 * An in-memory signed prekey store that is intended to be used temporarily while decrypting messages.
 */
class BufferedSignedPreKeyStore(private val selfServiceId: ServiceId) : SignedPreKeyStore {

  /** Our in-memory cache of signed prekeys. */
  private val store: MutableMap<Int, SignedPreKeyRecord> = HashMap()

  /** The signed prekeys that have been marked as removed  */
  private val removed: MutableList<Int> = mutableListOf()

  /** Whether or not we've done a loadAll operation. Let's us avoid doing it twice. */
  private var hasLoadedAll: Boolean = false

  @kotlin.jvm.Throws(InvalidKeyIdException::class)
  override fun loadSignedPreKey(id: Int): SignedPreKeyRecord {
    return store.computeIfAbsent(id) {
      SignalDatabase.signedPreKeys.get(selfServiceId, id) ?: throw InvalidKeyIdException("Missing one-time prekey with ID: $id")
    }
  }

  override fun loadSignedPreKeys(): List<SignedPreKeyRecord> {
    return if (hasLoadedAll) {
      store.values.toList()
    } else {
      val records = SignalDatabase.signedPreKeys.getAll(selfServiceId)
      records.forEach { store[it.id] = it }
      hasLoadedAll = true

      records
    }
  }

  override fun storeSignedPreKey(id: Int, record: SignedPreKeyRecord) {
    error("Should not happen during the intended usage pattern of this class")
  }

  override fun containsSignedPreKey(id: Int): Boolean {
    loadSignedPreKey(id)
    return store.containsKey(id)
  }

  override fun removeSignedPreKey(id: Int) {
    store.remove(id)
    removed += id
  }

  fun flushToDisk(persistentStore: SignalServiceAccountDataStore) {
    for (id in removed) {
      persistentStore.removeSignedPreKey(id)
    }

    removed.clear()
  }
}
