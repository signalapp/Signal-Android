package org.thoughtcrime.securesms.messages.protocol

import org.signal.core.models.ServiceId
import org.signal.core.models.ServiceId.PNI
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore

/**
 * The entry point for creating and retrieving buffered protocol stores.
 * These stores will read from disk, but never write, instead buffering the results in memory.
 * You can then call [flushToDisk] in order to write the buffered results to disk.
 *
 * This allows you to efficiently do batches of work and avoid unnecessary intermediate writes.
 */
class BufferedProtocolStore private constructor(
  private val aciStore: Pair<ServiceId, BufferedSignalServiceAccountDataStore>,
  private val pniStore: Pair<PNI, BufferedSignalServiceAccountDataStore>?
) {

  /** The PNI captured when this batch's store was created, or null if the account had no PNI. Does not refresh if [SignalStore.account.pni] later changes mid-batch. */
  val pni: PNI? get() = pniStore?.first

  fun get(serviceId: ServiceId): BufferedSignalServiceAccountDataStore {
    return when {
      serviceId == aciStore.first -> aciStore.second
      pniStore != null && serviceId == pniStore.first -> pniStore.second
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
    aciStore.second.flushToDisk(AppDependencies.protocolStore.aci())

    if (pniStore != null) {
      val diskPniStore = AppDependencies.protocolStore.pniOrNull()
      if (diskPniStore != null) {
        pniStore.second.flushToDisk(diskPniStore)
      } else {
        Log.w(TAG, "Have buffered PNI data, but the account no longer has a PNI store! Discarding it.")
      }
    }
  }

  companion object {
    private val TAG = Log.tag(BufferedProtocolStore::class)

    fun create(): BufferedProtocolStore {
      val aci = SignalStore.account.requireAci()
      val pni = SignalStore.account.pni

      return BufferedProtocolStore(
        aciStore = aci to BufferedSignalServiceAccountDataStore(aci),
        pniStore = pni?.let { it to BufferedSignalServiceAccountDataStore(it) }
      )
    }
  }
}
