package org.thoughtcrime.securesms.messages.protocol

import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.whispersystems.signalservice.api.push.ServiceId

/**
 * The entry point for creating and retrieving buffered protocol stores.
 * These stores will read from disk, but never write, instead buffering the results in memory.
 * You can then call [flushToDisk] in order to write the buffered results to disk.
 *
 * This allows you to efficiently do batches of work and avoid unnecessary intermediate writes.
 */
class BufferedProtocolStore private constructor(
  private val aciStore: Pair<ServiceId, BufferedSignalServiceAccountDataStore>,
  private val pniStore: Pair<ServiceId, BufferedSignalServiceAccountDataStore>
) {

  fun get(serviceId: ServiceId): BufferedSignalServiceAccountDataStore {
    return when (serviceId) {
      aciStore.first -> aciStore.second
      pniStore.first -> pniStore.second
      else -> error("No store matching serviceId $serviceId")
    }
  }

  fun getAciStore(): BufferedSignalServiceAccountDataStore {
    return aciStore.second
  }

  /**
   * Writes any buffered data to disk. You can continue to use the same buffered store afterwards.
   */
  fun flushToDisk() {
    aciStore.second.flushToDisk(ApplicationDependencies.getProtocolStore().aci())
    pniStore.second.flushToDisk(ApplicationDependencies.getProtocolStore().pni())
  }

  companion object {
    fun create(): BufferedProtocolStore {
      val aci = SignalStore.account().requireAci()
      val pni = SignalStore.account().requirePni()

      return BufferedProtocolStore(
        aciStore = aci to BufferedSignalServiceAccountDataStore(aci),
        pniStore = pni to BufferedSignalServiceAccountDataStore(pni)
      )
    }
  }
}
