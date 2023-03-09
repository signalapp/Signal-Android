package org.thoughtcrime.securesms.messages.protocol

import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.groups.state.SenderKeyRecord
import org.thoughtcrime.securesms.database.SignalDatabase
import org.whispersystems.signalservice.api.SignalServiceAccountDataStore
import org.whispersystems.signalservice.api.SignalServiceSenderKeyStore
import org.whispersystems.signalservice.api.push.DistributionId
import java.util.UUID

/**
 * An in-memory sender key store that is intended to be used temporarily while decrypting messages.
 */
class BufferedSenderKeyStore : SignalServiceSenderKeyStore {

  private val store: MutableMap<StoreKey, SenderKeyRecord> = HashMap()

  /** All of the keys that have been created or updated during operation. */
  private val updatedKeys: MutableMap<StoreKey, SenderKeyRecord> = mutableMapOf()

  /** All of the distributionId's whose sharing has been cleared during operation. */
  private val clearSharedWith: MutableSet<SignalProtocolAddress> = mutableSetOf()

  override fun storeSenderKey(sender: SignalProtocolAddress, distributionId: UUID, record: SenderKeyRecord) {
    val key = StoreKey(sender, distributionId)
    store[key] = record
    updatedKeys[key] = record
  }

  override fun loadSenderKey(sender: SignalProtocolAddress, distributionId: UUID): SenderKeyRecord? {
    val cached: SenderKeyRecord? = store[StoreKey(sender, distributionId)]

    return if (cached != null) {
      cached
    } else {
      val fromDatabase: SenderKeyRecord? = SignalDatabase.senderKeys.load(sender, distributionId.toDistributionId())

      if (fromDatabase != null) {
        store[StoreKey(sender, distributionId)] = fromDatabase
      }

      return fromDatabase
    }
  }

  override fun clearSenderKeySharedWith(addresses: MutableCollection<SignalProtocolAddress>) {
    clearSharedWith.addAll(addresses)
  }

  override fun getSenderKeySharedWith(distributionId: DistributionId?): MutableSet<SignalProtocolAddress> {
    error("Should not happen during the intended usage pattern of this class")
  }

  override fun markSenderKeySharedWith(distributionId: DistributionId?, addresses: MutableCollection<SignalProtocolAddress>?) {
    error("Should not happen during the intended usage pattern of this class")
  }

  fun flushToDisk(persistentStore: SignalServiceAccountDataStore) {
    for ((key, record) in updatedKeys) {
      persistentStore.storeSenderKey(key.address, key.distributionId, record)
    }

    persistentStore.clearSenderKeySharedWith(clearSharedWith)

    updatedKeys.clear()
    clearSharedWith.clear()
  }

  private fun UUID.toDistributionId() = DistributionId.from(this)

  data class StoreKey(
    val address: SignalProtocolAddress,
    val distributionId: UUID
  )
}
