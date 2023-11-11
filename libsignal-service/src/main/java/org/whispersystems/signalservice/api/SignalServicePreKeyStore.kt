package org.whispersystems.signalservice.api

import org.signal.libsignal.protocol.state.PreKeyStore

/**
 * And extension of the normal protocol prekey store interface that has additional methods that are
 * needed in the service layer, but not the protocol layer.
 */
interface SignalServicePreKeyStore : PreKeyStore {
  /**
   * Marks all prekeys stale if they haven't been marked already. "Stale" means the time that the keys have been replaced.
   */
  fun markAllOneTimeEcPreKeysStaleIfNecessary(staleTime: Long)

  /**
   * Deletes all prekeys that have been stale since before the threshold. "Stale" means the time that the keys have been replaced.
   */
  fun deleteAllStaleOneTimeEcPreKeys(threshold: Long, minCount: Int)
}
