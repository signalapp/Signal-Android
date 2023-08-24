package org.whispersystems.signalservice.api

import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.KyberPreKeyStore

/**
 * And extension of the normal protocol sender key store interface that has additional methods that are
 * needed in the service layer, but not the protocol layer.
 */
interface SignalServiceKyberPreKeyStore : KyberPreKeyStore {

  /**
   * Identical to [storeKyberPreKey] but indicates that this is a last-resort key rather than a one-time key.
   */
  fun storeLastResortKyberPreKey(kyberPreKeyId: Int, kyberPreKeyRecord: KyberPreKeyRecord)

  /**
   * Retrieves all last-resort kyber prekeys.
   */
  fun loadLastResortKyberPreKeys(): List<KyberPreKeyRecord>

  /**
   * Unconditionally remove the specified key from the store.
   */
  fun removeKyberPreKey(kyberPreKeyId: Int)

  /**
   * Marks all prekeys stale if they haven't been marked already. "Stale" means the time that the keys have been replaced.
   */
  fun markAllOneTimeKyberPreKeysStaleIfNecessary(staleTime: Long)

  /**
   * Deletes all prekeys that have been stale since before the threshold. "Stale" means the time that the keys have been replaced.
   */
  fun deleteAllStaleOneTimeKyberPreKeys(threshold: Long, minCount: Int)
}
